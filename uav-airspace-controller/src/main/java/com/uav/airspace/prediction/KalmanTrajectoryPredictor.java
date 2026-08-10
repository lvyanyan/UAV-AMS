package com.uav.airspace.prediction;

import com.uav.airspace.state.DroneStateSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 卡尔曼滤波轨迹预测器
 * <p>
 * 状态模型：6 维 — [x_east, x_north, x_up, v_east, v_north, v_up]
 * 使用恒定速度模型（Constant Velocity, CV），过程噪声体现加速度扰动。
 * <p>
 * 矩阵运算使用 EJML（Efficient Java Matrix Library），Apache-2.0 许可，纯 Java 无风险依赖。
 */
@Slf4j
@Component
public class KalmanTrajectoryPredictor {

    // 状态维度
    private static final int STATE_DIM = 6;
    // 观测维度（直接观测位置）
    private static final int MEAS_DIM = 3;

    // ─── 过程噪声参数 ───
    /** 加速度谱密度（m²/s³），体现机动不确定性 */
    private static final double PROCESS_NOISE_Q = 0.5;

    // ─── 观测噪声参数 ───
    /** GPS 位置观测噪声标准差（米） */
    private static final double MEASUREMENT_NOISE_R = 3.0;

    private final UncertaintyModel uncertaintyModel;

    public KalmanTrajectoryPredictor(com.uav.airspace.config.AirspaceControllerProperties props) {
        this.uncertaintyModel = new UncertaintyModel(props.getUncertaintyGrowthRateMps());
    }

    /**
     * 基于当前状态快照，预测未来 N 秒轨迹包络
     *
     * @param snapshot   当前无人机状态
     * @param totalSteps 预测步数
     * @param stepSec    每步秒数
     * @return 预测包络
     */
    public PredictionEnvelope predict(DroneStateSnapshot snapshot, int totalSteps, double stepSec) {

        // 1. 初始化状态向量和协方差矩阵
        DMatrixRMaj state = initState(snapshot);
        DMatrixRMaj cov = initCovariance();

        // 2. 状态转移矩阵 F (dt = stepSec)
        DMatrixRMaj F = buildTransitionMatrix(stepSec);

        // 3. 过程噪声协方差 Q
        DMatrixRMaj Q = buildProcessNoise(stepSec);

        // 4. 观测矩阵 H（只观测位置）
        DMatrixRMaj H = buildMeasurementMatrix();

        // 5. 观测噪声协方差 R
        DMatrixRMaj R = buildMeasurementNoise();

        // ─── 逐步预测 ───
        List<double[]> centerLine = new ArrayList<>();
        List<double[]> semiAxes = new ArrayList<>();

        // 第 0 步：当前位置（不含不确定性）
        centerLine.add(new double[]{
                snapshot.getLatestTelemetry().getLat(),
                snapshot.getLatestTelemetry().getLon(),
                snapshot.getLatestTelemetry().getAlt()
        });
        semiAxes.add(new double[]{3.0, 3.0, 1.5}); // 当前 GPS 误差

        DMatrixRMaj currentState = state.copy();
        DMatrixRMaj currentCov = cov.copy();

        for (int step = 1; step <= totalSteps; step++) {
            // 预测步骤
            DMatrixRMaj predictedState = new DMatrixRMaj(STATE_DIM, 1);
            CommonOps_DDRM.mult(F, currentState, predictedState);

            DMatrixRMaj predictedCov = new DMatrixRMaj(STATE_DIM, STATE_DIM);
            DMatrixRMaj temp = new DMatrixRMaj(STATE_DIM, STATE_DIM);
            CommonOps_DDRM.mult(F, currentCov, temp);
            CommonOps_DDRM.multTransB(temp, F, predictedCov);
            CommonOps_DDRM.add(predictedCov, Q, predictedCov);

            // 位置提取（lat = north → 近似, lon = east → 近似）
            double eastOffset = predictedState.get(0, 0);   // 东向位移（米）
            double northOffset = predictedState.get(1, 0);  // 北向位移（米）
            double altOffset = predictedState.get(2, 0);    // 天向位移（米）

            // 米 → 度（WGS84 近似，纬度 30° 附近）
            double latDegPerMeter = 1.0 / 111320.0;
            double lonDegPerMeter = 1.0 / (111320.0 * Math.cos(Math.toRadians(
                    snapshot.getLatestTelemetry().getLat())));

            double predLat = snapshot.getLatestTelemetry().getLat() + northOffset * latDegPerMeter;
            double predLon = snapshot.getLatestTelemetry().getLon() + eastOffset * lonDegPerMeter;
            double predAlt = snapshot.getLatestTelemetry().getAlt() + altOffset;

            centerLine.add(new double[]{predLat, predLon, predAlt});

            // 不确定性（从协方差对角元提取 + 不确定性模型）
            double sigmaEast = Math.sqrt(Math.max(predictedCov.get(0, 0), 0)) * 3;
            double sigmaNorth = Math.sqrt(Math.max(predictedCov.get(1, 1), 0)) * 3;
            double sigmaUp = Math.sqrt(Math.max(predictedCov.get(2, 2), 0)) * 3;

            // 叠加线性不确定膨胀（气象/环境因素）
            double predTimeSec = step * stepSec;
            double[] envUncertainty = uncertaintyModel.computeUncertainty(predTimeSec);
            sigmaEast = Math.max(sigmaEast, envUncertainty[0]);
            sigmaNorth = Math.max(sigmaNorth, envUncertainty[1]);
            sigmaUp = Math.max(sigmaUp, envUncertainty[2]);

            semiAxes.add(new double[]{sigmaEast, sigmaNorth, sigmaUp});

            // 更新
            currentState = predictedState;
            currentCov = predictedCov;

            // 协方差发散检查
            if (sigmaEast > 500 || sigmaNorth > 500) {
                log.debug("无人机 {} 预测协方差发散 (step={}, σ={}), 截断",
                        snapshot.getDroneSn(), step, sigmaEast);
                break;
            }
        }

        return PredictionEnvelope.builder()
                .droneSn(snapshot.getDroneSn())
                .generatedAt(System.currentTimeMillis())
                .totalSteps(centerLine.size() - 1)
                .stepSeconds(stepSec)
                .centerLine(centerLine)
                .uncertaintySemiAxes(semiAxes)
                .build();
    }

    // ═══════════════════════════════════════════
    // 矩阵构造方法
    // ═══════════════════════════════════════════

    private DMatrixRMaj initState(DroneStateSnapshot snap) {
        DMatrixRMaj state = new DMatrixRMaj(STATE_DIM, 1);
        // 位置初始化为 0（相对坐标系原点）
        state.set(0, 0, 0); // x_east
        state.set(1, 0, 0); // x_north
        state.set(2, 0, 0); // x_up
        state.set(3, 0, snap.getVelocityEast());
        state.set(4, 0, snap.getVelocityNorth());
        state.set(5, 0, snap.getVelocityUp());
        return state;
    }

    private DMatrixRMaj initCovariance() {
        DMatrixRMaj cov = new DMatrixRMaj(STATE_DIM, STATE_DIM);
        // 初始位置不确定性
        double posVar = MEASUREMENT_NOISE_R * MEASUREMENT_NOISE_R;
        // 初始速度不确定性（未知 → 较大方差）
        double velVar = 25.0; // 5 m/s std
        cov.set(0, 0, posVar);
        cov.set(1, 1, posVar);
        cov.set(2, 2, posVar);
        cov.set(3, 3, velVar);
        cov.set(4, 4, velVar);
        cov.set(5, 5, velVar);
        return cov;
    }

    private DMatrixRMaj buildTransitionMatrix(double dt) {
        DMatrixRMaj F = new DMatrixRMaj(STATE_DIM, STATE_DIM);
        // 位置 = 位置 + 速度 × dt
        for (int i = 0; i < STATE_DIM; i++) F.set(i, i, 1.0);
        F.set(0, 3, dt); // x_east += v_east * dt
        F.set(1, 4, dt); // x_north += v_north * dt
        F.set(2, 5, dt); // x_up += v_up * dt
        return F;
    }

    private DMatrixRMaj buildProcessNoise(double dt) {
        DMatrixRMaj Q = new DMatrixRMaj(STATE_DIM, STATE_DIM);
        double dt2 = dt * dt / 2.0;
        double dt3 = dt * dt * dt / 3.0;
        double q = PROCESS_NOISE_Q;
        // CV 模型过程噪声（离散白噪声加速度模型）
        for (int i = 0; i < 3; i++) {
            Q.set(i, i, q * dt3);
            Q.set(i, i + 3, q * dt2);
            Q.set(i + 3, i, q * dt2);
            Q.set(i + 3, i + 3, q * dt);
        }
        return Q;
    }

    private DMatrixRMaj buildMeasurementMatrix() {
        DMatrixRMaj H = new DMatrixRMaj(MEAS_DIM, STATE_DIM);
        H.set(0, 0, 1.0);
        H.set(1, 1, 1.0);
        H.set(2, 2, 1.0);
        return H;
    }

    private DMatrixRMaj buildMeasurementNoise() {
        DMatrixRMaj R = new DMatrixRMaj(MEAS_DIM, MEAS_DIM);
        double var = MEASUREMENT_NOISE_R * MEASUREMENT_NOISE_R;
        R.set(0, 0, var);
        R.set(1, 1, var);
        R.set(2, 2, var);
        return R;
    }
}
