import { describe, it, expect, vi } from 'vitest';
import { render, fireEvent, waitFor } from '@testing-library/react';
import { AppContent } from './StudentApp';
import { AuthContext } from './contexts/AuthContext';
import { AssessmentContext } from './contexts/AssessmentContext';
import { ThemeProvider } from './contexts/ThemeContext';
import { SnackbarProvider } from './contexts/SnackbarContext';

vi.mock('./api/intakeApi', () => ({
  intakeApi: {
    getReferrals: vi.fn().mockResolvedValue([]),
    getReferralDetails: vi.fn(),
    getReferralTracking: vi.fn(),
    getAssessmentCatalog: vi.fn().mockResolvedValue([
      { batteryCode: 'b', title: '量表X', duration: '5分钟', questionCount: 10, isEnabled: true },
    ]),
    getStaffStudents: vi.fn().mockResolvedValue([{ id: '1', name: '李明', studentNumber: 'S1', major: 'CS' }]),
    getScales: vi.fn().mockResolvedValue([]),
  },
  tokenStorage: { get: () => 't', set: vi.fn(), clear: vi.fn() },
}));

describe('teacher tab routing', () => {
  it('clicking 发放问卷 shows dispatch page, not referrals', async () => {
    render(
      <ThemeProvider>
        <SnackbarProvider>
          <AuthContext.Provider
            value={{
              student: { studentNumber: 'EMP-00001', fullName: 'T', phone: '', registeredAt: '' },
              token: 't',
              role: 'TEACHER',
              loading: false,
              login: vi.fn(),
              loginWithSms: vi.fn(),
              register: vi.fn(),
              logout: vi.fn(),
              refreshStudent: vi.fn(),
            } as any}
          >
            <AssessmentContext.Provider
              value={{
                totalCount: 1,
                completedCount: 0,
                remainingCount: 1,
                isAllCompleted: false,
                loading: false,
                filter: 'all',
                setFilter: vi.fn(),
                isScrolled: false,
                setIsScrolled: vi.fn(),
                scales: [],
                refreshScales: vi.fn(),
              } as any}
            >
              <AppContent />
            </AssessmentContext.Provider>
          </AuthContext.Provider>
        </SnackbarProvider>
      </ThemeProvider>
    );

    const tabs = document.querySelectorAll('md-navigation-tab');
    const labels = Array.from(tabs).map(t => (t as any).label ?? t.getAttribute('label'));
    expect(labels).toEqual(['问卷测评', '发放问卷', '转诊进度', '个人中心']);

    fireEvent.click(tabs[1]);
    await waitFor(() => expect(document.querySelector('h2')?.textContent).toBe('发放问卷'));

    fireEvent.click(tabs[2]);
    await waitFor(() => expect(document.querySelector('h2')?.textContent).toBe('转诊进度'));
  });
});
