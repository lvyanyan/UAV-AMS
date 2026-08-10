package com.uav.track.fitting;

import com.uav.track.model.PositionSource;
import com.uav.track.model.RawPosition;
import lombok.extern.slf4j.Slf4j;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * RTS（Rauch–Tung–Striebel）卡尔曼平滑器
 * <p>
 * 与 airspace-controller 中的 KalmanTrajectoryPredictor 的区别：
 * <ul>
 *   <li>Predictor: 前向预测（未来轨迹），用于冲突检测</li>
 *   <li>本类: RTS 平滑（历史轨迹最佳估计），用于轨迹拟合</li>
 * </ul>
 * <p>
 * 流程：前向卡尔曼滤波 → 存储所有状态 → 反向平滑 → 输出最佳估计轨迹
 */
@Slf4j
@Component
public class KalmanTrackSmoother {

    private static final int STATE_DIM = 6;   // [x_east, x_north, x_up, v_east, v_north, v_up]
    private static final int MEAS_DIM = 3;    // 观测 [x, y, z]

    /** 默认过程噪声谱密度 */
    private static final double DEFAULT_Q = 0.3;

    /**
     * 对一组原始位置进行 RTS 平滑
     *
     * @param positions 原始位置列表（按时间有序）
     * @return 平滑后的位置 [lat, lon, alt] 列表
     */
    public List<double[]> smooth(List<RawPosition> positions) {
        if (positions == null || positions.size() < 3) {
            // 点太少，直接返回原始值
            return positions == null ? List.of()
                    : positions.stream()
                    .map(p -> new double[]{p.getLatitude(), p.getLongitude(), p.getAltitude()})
                    .toList();
        }

        int n = positions.size();

        // ── 前向卡尔曼滤波 ──
        List<DMatrixRMaj> statesFwd = new ArrayList<>(n);
        List<DMatrixRMaj> covsFwd = new ArrayList<>(n);

        double refLat = positions.get(0).getLatitude();
        double refLon = positions.get(0).getLongitude();

        DMatrixRMaj state = initState(positions.get(0), refLat, refLon);
        DMatrixRMaj cov = initCovariance(positions.get(0).getSource());

        statesFwd.add(state.copy());
        covsFwd.add(cov.copy());

        for (int i = 1; i < n; i++) {
            RawPosition prev = positions.get(i - 1);
            RawPosition curr = positions.get(i);
            double dt = (curr.getTimestamp().toEpochMilli() - prev.getTimestamp().toEpochMilli()) / 1000.0;
            if (dt <= 0) dt = 0.2; // 最小步长

            DMatrixRMaj F = buildTransition(dt);
            DMatrixRMaj Q = buildProcessNoise(dt, DEFAULT_Q);

            // 预测
            DMatrixRMaj predState = new DMatrixRMaj(STATE_DIM, 1);
            CommonOps_DDRM.mult(F, state, predState);

            DMatrixRMaj predCov = new DMatrixRMaj(STATE_DIM, STATE_DIM);
            DMatrixRMaj temp = new DMatrixRMaj(STATE_DIM, STATE_DIM);
            CommonOps_DDRM.mult(F, cov, temp);
            CommonOps_DDRM.multTransB(temp, F, predCov);
            CommonOps_DDRM.add(predCov, Q, predCov);

            // 观测
            DMatrixRMaj z = positionToMeasurement(curr, refLat, refLon);
            double r = getMeasurementNoise(curr.getSource());

            // 更新（标准卡尔曼）
            DMatrixRMaj H = buildMeasurement();
            DMatrixRMaj S = new DMatrixRMaj(MEAS_DIM, MEAS_DIM);
            DMatrixRMaj PHt = new DMatrixRMaj(STATE_DIM, MEAS_DIM);
            CommonOps_DDRM.multTransB(predCov, H, PHt);
            CommonOps_DDRM.mult(H, PHt, S);
            S.set(0, 0, S.get(0, 0) + r * r);
            S.set(1, 1, S.get(1, 1) + r * r);
            S.set(2, 2, S.get(2, 2) + r * r);

            // 卡尔曼增益 K = P·Hᵗ·S⁻¹
            DMatrixRMaj K = new DMatrixRMaj(STATE_DIM, MEAS_DIM);
            DMatrixRMaj S_inv = new DMatrixRMaj(MEAS_DIM, MEAS_DIM);
            // 简单对角逆矩阵（S 是对角的）
            S_inv.set(0, 0, 1.0 / Math.max(S.get(0, 0), 1e-9));
            S_inv.set(1, 1, 1.0 / Math.max(S.get(1, 1), 1e-9));
            S_inv.set(2, 2, 1.0 / Math.max(S.get(2, 2), 1e-9));
            CommonOps_DDRM.mult(PHt, S_inv, K);

            // 状态更新
            DMatrixRMaj innovation = new DMatrixRMaj(MEAS_DIM, 1);
            CommonOps_DDRM.subtract(z, CommonOps_DDRM.mult(H, predState, null), innovation);
            DMatrixRMaj updatedState = new DMatrixRMaj(STATE_DIM, 1);
            CommonOps_DDRM.add(predState, CommonOps_DDRM.mult(K, innovation, null), updatedState);

            // 协方差更新: P = (I - K·H)·P_pred
            DMatrixRMaj I = CommonOps_DDRM.identity(STATE_DIM);
            DMatrixRMaj KH = new DMatrixRMaj(STATE_DIM, STATE_DIM);
            CommonOps_DDRM.mult(K, H, KH);
            DMatrixRMaj I_KH = new DMatrixRMaj(STATE_DIM, STATE_DIM);
            CommonOps_DDRM.subtract(I, KH, I_KH);
            DMatrixRMaj updatedCov = new DMatrixRMaj(STATE_DIM, STATE_DIM);
            CommonOps_DDRM.mult(I_KH, predCov, updatedCov);

            state = updatedState;
            cov = updatedCov;
            statesFwd.add(state.copy());
            covsFwd.add(cov.copy());
        }

        // ── 反向 RTS 平滑 ──
        List<DMatrixRMaj> statesSmoothed = new ArrayList<>(n);
        for (int i = 0; i < n; i++) statesSmoothed.add(null);
        statesSmoothed.set(n - 1, statesFwd.get(n - 1).copy());

        for (int i = n - 2; i >= 0; i--) {
            RawPosition curr = positions.get(i);
            RawPosition next = positions.get(i + 1);
            double dt = (next.getTimestamp().toEpochMilli() - curr.getTimestamp().toEpochMilli()) / 1000.0;
            if (dt <= 0) dt = 0.2;

            DMatrixRMaj F = buildTransition(dt);
            DMatrixRMaj Q = buildProcessNoise(dt, DEFAULT_Q);

            // 预测协方差: P_pred = F·P_i·Fᵗ + Q
            DMatrixRMaj P_pred = new DMatrixRMaj(STATE_DIM, STATE_DIM);
            DMatrixRMaj temp = new DMatrixRMaj(STATE_DIM, STATE_DIM);
            CommonOps_DDRM.mult(F, covsFwd.get(i), temp);
            CommonOps_DDRM.multTransB(temp, F, P_pred);
            CommonOps_DDRM.add(P_pred, Q, P_pred);

            // 平滑增益: G = P_i·Fᵗ·P_pred⁻¹
            DMatrixRMaj P_i_Ft = new DMatrixRMaj(STATE_DIM, STATE_DIM);
            CommonOps_DDRM.multTransB(covsFwd.get(i), F, P_i_Ft);

            // P_pred 对角近似求逆
            DMatrixRMaj P_pred_inv = new DMatrixRMaj(STATE_DIM, STATE_DIM);
            for (int j = 0; j < STATE_DIM; j++) {
                P_pred_inv.set(j, j, 1.0 / Math.max(P_pred.get(j, j), 1e-9));
            }

            DMatrixRMaj G = new DMatrixRMaj(STATE_DIM, STATE_DIM);
            CommonOps_DDRM.mult(P_i_Ft, P_pred_inv, G);

            // 平滑状态: x_s = x_i + G·(x_s_next - x_pred)
            DMatrixRMaj x_pred = new DMatrixRMaj(STATE_DIM, 1);
            CommonOps_DDRM.mult(F, statesFwd.get(i), x_pred);

            DMatrixRMaj diff = new DMatrixRMaj(STATE_DIM, 1);
            CommonOps_DDRM.subtract(statesSmoothed.get(i + 1), x_pred, diff);

            DMatrixRMaj x_smooth = new DMatrixRMaj(STATE_DIM, 1);
            CommonOps_DDRM.add(statesFwd.get(i), CommonOps_DDRM.mult(G, diff, null), x_smooth);

            statesSmoothed.set(i, x_smooth);
        }

        // ── 输出平滑后的位置 ──
        List<double[]> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            DMatrixRMaj s = statesSmoothed.get(i);
            double eastM = s.get(0, 0);
            double northM = s.get(1, 0);
            double upM = s.get(2, 0);

            double latDegPerMeter = 1.0 / 111320.0;
            double lonDegPerMeter = 1.0 / (111320.0 * Math.cos(Math.toRadians(refLat)));

            double smoothLat = refLat + northM * latDegPerMeter;
            double smoothLon = refLon + eastM * lonDegPerMeter;
            double smoothAlt = positions.get(i).getAltitude() + upM;

            result.add(new double[]{smoothLat, smoothLon, smoothAlt});
        }

        return result;
    }

    // ══════════════════════════════════════
    // 矩阵构造
    // ══════════════════════════════════════

    private DMatrixRMaj initState(RawPosition p, double refLat, double refLon) {
        DMatrixRMaj s = new DMatrixRMaj(STATE_DIM, 1);
        s.zero();
        return s;
    }

    private DMatrixRMaj initCovariance(PositionSource source) {
        DMatrixRMaj P = new DMatrixRMaj(STATE_DIM, STATE_DIM);
        double posVar = getMeasurementNoise(source) * getMeasurementNoise(source);
        double velVar = 25.0;
        P.set(0, 0, posVar); P.set(1, 1, posVar); P.set(2, 2, posVar);
        P.set(3, 3, velVar); P.set(4, 4, velVar); P.set(5, 5, velVar);
        return P;
    }

    private DMatrixRMaj buildTransition(double dt) {
        DMatrixRMaj F = CommonOps_DDRM.identity(STATE_DIM);
        F.set(0, 3, dt);
        F.set(1, 4, dt);
        F.set(2, 5, dt);
        return F;
    }

    private DMatrixRMaj buildProcessNoise(double dt, double q) {
        DMatrixRMaj Q = new DMatrixRMaj(STATE_DIM, STATE_DIM);
        double dt2 = dt * dt / 2.0;
        double dt3 = dt * dt * dt / 3.0;
        for (int i = 0; i < 3; i++) {
            Q.set(i, i, q * dt3);
            Q.set(i, i + 3, q * dt2);
            Q.set(i + 3, i, q * dt2);
            Q.set(i + 3, i + 3, q * dt);
        }
        return Q;
    }

    private DMatrixRMaj buildMeasurement() {
        DMatrixRMaj H = new DMatrixRMaj(MEAS_DIM, STATE_DIM);
        H.set(0, 0, 1); H.set(1, 1, 1); H.set(2, 2, 1);
        return H;
    }

    private DMatrixRMaj positionToMeasurement(RawPosition p, double refLat, double refLon) {
        DMatrixRMaj z = new DMatrixRMaj(MEAS_DIM, 1);
        double latDegPerMeter = 1.0 / 111320.0;
        double lonDegPerMeter = 1.0 / (111320.0 * Math.cos(Math.toRadians(refLat)));
        z.set(0, 0, (p.getLongitude() - refLon) / lonDegPerMeter);
        z.set(1, 0, (p.getLatitude() - refLat) / latDegPerMeter);
        z.set(2, 0, 0.0); // 高度不转换
        return z;
    }

    private double getMeasurementNoise(PositionSource source) {
        if (source == null) return 10.0;
        return source.getAccuracyMeters();
    }
}
