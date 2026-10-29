import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { AppContent } from './StudentApp';
import { AuthContext } from './contexts/AuthContext';
import { AssessmentContext } from './contexts/AssessmentContext';
import { ThemeProvider } from './contexts/ThemeContext';
import { SnackbarProvider } from './contexts/SnackbarContext';

describe('StudentApp', () => {
  const mockStudent = {
    studentNumber: '8209220532',
    fullName: '张三',
    phone: '13800138000',
    registeredAt: '2026-09-01T00:00:00',
  };

  const mockScales = [
    {
      code: 'comprehensive_student_intake_survey',
      title: '中南大学学生心身健康综合调查问卷',
      description: '综合普查',
      questionCount: 336,
      estimatedMinutes: 45,
      status: 'NOT_STARTED' as const,
      answeredCount: 0,
      completionPercentage: 0,
    },
  ];

  const renderWithProviders = (authState: any, assessmentState?: any) => {
    const defaultAuth: any = {
      student: mockStudent,
      loading: false,
      login: vi.fn(),
      logout: vi.fn(),
      setStudent: vi.fn(),
      ...authState,
    };

    const defaultAssessment: any = {
      scales: mockScales,
      loading: false,
      error: null,
      totalCount: 1,
      completedCount: 0,
      remainingCount: 1,
      isAllCompleted: false,
      refreshScales: vi.fn(),
      filter: 'all',
      setFilter: vi.fn(),
      isScrolled: false,
      setIsScrolled: vi.fn(),
      ...assessmentState,
    };

    return render(
      <AuthContext.Provider value={defaultAuth}>
        <AssessmentContext.Provider value={defaultAssessment}>
          <ThemeProvider>
            <SnackbarProvider>
              <AppContent />
            </SnackbarProvider>
          </ThemeProvider>
        </AssessmentContext.Provider>
      </AuthContext.Provider>
    );
  };

  it('renders loading splash when auth is loading', () => {
    renderWithProviders({ loading: true, student: null });
    expect(screen.getByText('正在初始化普查系统...')).toBeDefined();
  });

  it('renders AuthPage when not authenticated', () => {
    renderWithProviders({ loading: false, student: null });
    expect(screen.getByText('登录')).toBeDefined();
    expect(screen.getByText('使用您的中南大学学号以继续心理普查')).toBeDefined();
  });

  it('renders student top bar and assessments page when authenticated', async () => {
    renderWithProviders({});

    expect(screen.getByText('中南大学 · 心理普查')).toBeDefined();
    expect(screen.getByText('心身健康普查问卷待填报')).toBeDefined();
    expect(screen.getByText('张三 (8209220532)')).toBeDefined();

    // Verify unified questionnaire is rendered
    expect(screen.getByText('中南大学学生心身健康综合调查问卷')).toBeDefined();
  });

  it('updates header when all tasks are completed', () => {
    renderWithProviders(
      {},
      {
        totalCount: 1,
        completedCount: 1,
        remainingCount: 0,
        isAllCompleted: true,
        scales: [{ ...mockScales[0], status: 'COMPLETED' as const }],
      }
    );

    expect(screen.getByText('全部测评任务已完成')).toBeDefined();
  });

  it('switches between 问卷测评 and 个人中心 tabs', () => {
    renderWithProviders({});

    const tabs = document.querySelectorAll('md-navigation-tab');
    expect(tabs.length).toBe(3);
    expect((tabs[0] as any).label ?? tabs[0].getAttribute('label')).toBe('问卷测评');
    expect((tabs[1] as any).label ?? tabs[1].getAttribute('label')).toBe('转诊进度');
    expect((tabs[2] as any).label ?? tabs[2].getAttribute('label')).toBe('个人中心');

    // Click on 个人中心 tab
    fireEvent.click(tabs[2]);

    // Verify Profile view is displayed
    expect(screen.getByText('个人信息')).toBeDefined();
    expect(screen.getByText('医疗与数据安全须知')).toBeDefined();
  });
});
