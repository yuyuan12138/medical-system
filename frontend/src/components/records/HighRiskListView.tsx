import * as React from 'react';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../../contexts/AuthContext';
import { useCreationOverlay } from '../../contexts/CreationContext';
import { fetchHighRiskStudents, HighRiskStudentDto } from '../../api/highRisk';
import { ReferralCreationForm } from './ReferralCreationForm';
import { PrimaryButton, OutlinedButton } from '../common/Buttons';
import { RISK_LEVEL_STYLES } from '../../config/styleConstants';
import { SevereRiskFactorType } from '../../types';

const RISK_LABELS: Record<string, string> = {
  HIGH: '高风险',
  MEDIUM: '中风险',
  LOW: '低风险',
};

const RISK_FLAG_LABELS: Record<string, string> = {
  SUICIDAL_IDEATION: '自杀意念',
  SUICIDE_ATTEMPT: '自杀企图',
  SELF_HARM: '自残行为',
};

export function HighRiskListView({ header }: { header?: (loading: boolean) => React.ReactNode }) {
  const { session } = useAuth();
  const { openCreation, closeCreation, expandToFullscreen } = useCreationOverlay();
  const [includeMedium, setIncludeMedium] = React.useState(false);

  const { data: students = [], isLoading, isError, refetch } = useQuery<HighRiskStudentDto[]>({
    queryKey: ['high-risk-students', includeMedium],
    queryFn: () => fetchHighRiskStudents(session?.token, includeMedium),
    enabled: !!session?.token,
  });

  const handleCreateReferral = (student: HighRiskStudentDto) => {
    const flags = student.riskFlags.filter((f): f is SevereRiskFactorType => f in RISK_FLAG_LABELS);
    openCreation(
      `发起转诊 - ${student.studentName}`,
      <ReferralCreationForm
        onClose={closeCreation}
        initialData={{
          studentId: String(student.studentId),
          title: `高风险筛查转诊 - ${student.studentName} (${student.studentNumber})`,
          riskLevel: student.riskStatus,
          severeRiskFactors: flags,
        }}
      />
    );
    setTimeout(expandToFullscreen, 10);
  };

  return (
    <div className="flex flex-col h-full">
      {header?.(isLoading)}
      <div className="flex-1 overflow-auto p-4">
        <div className="flex items-center gap-3 mb-4">
          <OutlinedButton
            label={includeMedium ? '仅显示高风险' : '包含中风险'}
            onClick={() => setIncludeMedium((v) => !v)}
          />
          <OutlinedButton label="刷新" onClick={() => refetch()} />
          <span className="text-sm text-[var(--md-sys-color-on-surface-variant)]">
            共 {students.length} 名学生
          </span>
        </div>

        {isError && (
          <div className="p-4 mb-4 rounded bg-[var(--md-sys-color-error-container)] text-[var(--md-sys-color-on-error-container)]">
            加载高危名单失败，请重试
          </div>
        )}

        {isLoading ? (
          <div className="p-8 text-center text-[var(--md-sys-color-on-surface-variant)]">加载中…</div>
        ) : students.length === 0 ? (
          <div className="p-8 text-center text-[var(--md-sys-color-on-surface-variant)]">
            当前没有{includeMedium ? '中高风险' : '高风险'}学生
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-[var(--md-sys-color-on-surface-variant)] border-b border-[var(--md-sys-color-outline-variant)]">
                <th className="py-2 pr-4 font-medium">学号</th>
                <th className="py-2 pr-4 font-medium">姓名</th>
                <th className="py-2 pr-4 font-medium">风险等级</th>
                <th className="py-2 pr-4 font-medium">风险标记</th>
                <th className="py-2 pr-4 font-medium">诊断</th>
                <th className="py-2 pr-4 font-medium">操作</th>
              </tr>
            </thead>
            <tbody>
              {students.map((s) => (
                <tr key={s.studentId} className="border-b border-[var(--md-sys-color-outline-variant)]/50">
                  <td className="py-2 pr-4">{s.studentNumber}</td>
                  <td className="py-2 pr-4">{s.studentName}</td>
                  <td className="py-2 pr-4">
                    <span className={`inline-block px-2 py-0.5 rounded text-xs ${RISK_LEVEL_STYLES[s.riskStatus] || ''}`}>
                      {RISK_LABELS[s.riskStatus] || s.riskStatus}
                    </span>
                  </td>
                  <td className="py-2 pr-4">
                    {s.riskFlags.length === 0 ? (
                      <span className="text-[var(--md-sys-color-on-surface-variant)]">—</span>
                    ) : (
                      <span className="inline-flex gap-1 flex-wrap">
                        {s.riskFlags.map((f) => (
                          <span key={f} className="inline-block px-2 py-0.5 rounded text-xs bg-[var(--md-sys-color-error-container)] text-[var(--md-sys-color-on-error-container)]">
                            {RISK_FLAG_LABELS[f] || f}
                          </span>
                        ))}
                      </span>
                    )}
                  </td>
                  <td className="py-2 pr-4">{s.scidDiagnosis || '—'}</td>
                  <td className="py-2 pr-4">
                    <PrimaryButton label="发起转诊" onClick={() => handleCreateReferral(s)} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
