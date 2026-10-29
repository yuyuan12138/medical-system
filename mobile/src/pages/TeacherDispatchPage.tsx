import React, { useEffect, useState, useCallback } from 'react';
import { intakeApi, AssessmentCatalogItem, StaffStudentDto } from '../api/intakeApi';
import { useSnackbar } from '../contexts/SnackbarContext';

export const TeacherDispatchPage: React.FC = () => {
  const { showSnackbar } = useSnackbar();
  const [catalog, setCatalog] = useState<AssessmentCatalogItem[]>([]);
  const [students, setStudents] = useState<StaffStudentDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [scale, setScale] = useState<string>('');
  const [selectedStudents, setSelectedStudents] = useState<Set<string>>(new Set());
  const [dueDate, setDueDate] = useState<string>('');
  const [search, setSearch] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [c, s] = await Promise.all([
        intakeApi.getAssessmentCatalog().catch(() => []),
        intakeApi.getStaffStudents().catch(() => []),
      ]);
      setCatalog(c.filter(item => item.isEnabled));
      if (c.length > 0) setScale(c[0].batteryCode);
      setStudents(s);
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const toggleStudent = (id: string) => {
    setSelectedStudents(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  };

  const filtered = students.filter(s =>
    !search || s.name.includes(search) || (s.studentNumber || '').includes(search)
  );

  const submit = async () => {
    if (!scale || selectedStudents.size === 0) return;
    setSubmitting(true);
    let ok = 0;
    let fail = 0;
    for (const sid of selectedStudents) {
      try {
        await intakeApi.assignAssessment(Number(sid), scale, dueDate || undefined);
        ok++;
      } catch {
        fail++;
      }
    }
    setSubmitting(false);
    setSelectedStudents(new Set());
    showSnackbar({ message: `发放完成：成功 ${ok} 人${fail > 0 ? `，失败 ${fail} 人` : ''}` });
  };

  return (
    <div className="flex flex-col gap-3">
      <h2 className="text-lg font-bold text-[var(--md-sys-color-on-surface)]">发放问卷</h2>

      {error && (
        <div className="p-3 rounded-xl bg-[var(--md-sys-color-error-container)] text-[var(--md-sys-color-on-error-container)] text-sm">
          {error}
        </div>
      )}

      {loading ? (
        <div className="py-10 text-center text-sm text-[var(--md-sys-color-on-surface-variant)]">加载中…</div>
      ) : (
        <>
          <div className="rounded-2xl bg-[var(--md-sys-color-surface-container)] p-4">
            <div className="text-sm font-semibold mb-2">选择量表</div>
            {catalog.length === 0 ? (
              <div className="text-sm text-[var(--md-sys-color-on-surface-variant)]">暂无可用量表</div>
            ) : catalog.map(c => (
              <label key={c.batteryCode} className="flex items-center gap-2 py-1.5 text-sm">
                <input
                  type="radio"
                  name="scale"
                  checked={scale === c.batteryCode}
                  onChange={() => setScale(c.batteryCode)}
                />
                <span className="text-[var(--md-sys-color-on-surface)]">{c.title}（{c.questionCount} 题 · 约 {c.duration}）</span>
              </label>
            ))}

            <div className="text-sm font-semibold mt-3 mb-1">截止日期（可选）</div>
            <input
              type="date"
              value={dueDate}
              onChange={e => setDueDate(e.target.value)}
              className="w-full p-2 rounded-xl bg-[var(--md-sys-color-surface)] text-sm text-[var(--md-sys-color-on-surface)]"
            />
          </div>

          <div className="rounded-2xl bg-[var(--md-sys-color-surface-container)] p-4">
            <div className="flex items-center justify-between mb-2">
              <div className="text-sm font-semibold">选择学生（已选 {selectedStudents.size} 人）</div>
              <button
                className="text-sm text-[var(--md-sys-color-primary)]"
                onClick={() => setSelectedStudents(new Set(filtered.map(s => s.id)))}
              >
                全选
              </button>
            </div>
            <input
              type="search"
              placeholder="搜索姓名或学号"
              value={search}
              onChange={e => setSearch(e.target.value)}
              className="w-full mb-2 p-2 rounded-xl bg-[var(--md-sys-color-surface)] text-sm text-[var(--md-sys-color-on-surface)]"
            />
            <div className="max-h-72 overflow-y-auto flex flex-col">
              {filtered.map(s => (
                <label key={s.id} className="flex items-center gap-2 py-1.5 text-sm">
                  <input
                    type="checkbox"
                    checked={selectedStudents.has(s.id)}
                    onChange={() => toggleStudent(s.id)}
                  />
                  <span className="text-[var(--md-sys-color-on-surface)]">{s.name}（{s.studentNumber}）</span>
                </label>
              ))}
              {filtered.length === 0 && (
                <div className="py-4 text-center text-sm text-[var(--md-sys-color-on-surface-variant)]">没有匹配的学生</div>
              )}
            </div>
          </div>

          <button
            className="w-full py-3 rounded-full text-sm font-semibold bg-[var(--md-sys-color-primary)] text-[var(--md-sys-color-on-primary)] disabled:opacity-50"
            disabled={submitting || !scale || selectedStudents.size === 0}
            onClick={submit}
          >
            {submitting ? '发放中…' : `发放给 ${selectedStudents.size} 名学生`}
          </button>
        </>
      )}
    </div>
  );
};
