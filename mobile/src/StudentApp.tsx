import { useState, useRef } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import { ThemeProvider, useTheme } from './contexts/ThemeContext';
import {
  AssessmentProvider,
  useAssessments,
  ASSESSMENT_FILTER_ITEMS,
  AssessmentFilterType,
} from './contexts/AssessmentContext';
import { SnackbarProvider } from './contexts/SnackbarContext';
import { SegmentedButton } from './components/common/Buttons';
import { AuthPage } from './pages/AuthPage';
import { AssessmentsPage } from './pages/AssessmentsPage';
import { ProfilePage } from './pages/ProfilePage';
import { ReferralsPage } from './pages/ReferralsPage';
import { TeacherDispatchPage } from './pages/TeacherDispatchPage';
import './utils/navigationTabStyles';

export function AppContent() {
  const { student, loading, role } = useAuth();
  const {
    totalCount,
    completedCount,
    remainingCount,
    isAllCompleted,
    loading: scalesLoading,
    filter,
    setFilter,
    isScrolled,
    setIsScrolled,
    scales,
  } = useAssessments();
  const { isDark, toggleTheme } = useTheme();
  const [activeTab, setActiveTab] = useState(0);
  const mainRef = useRef<HTMLElement>(null);

  const isTeacher = role === 'TEACHER' || role === 'HEAD_COUNSELLOR';
  const tabCount = isTeacher ? 4 : 3;

  const handleTabChange = (tab: number) => {
    if (tab >= tabCount) return;
    setActiveTab(tab);
    setIsScrolled(false);
    if (mainRef.current) {
      if (typeof mainRef.current.scrollTo === 'function') {
        mainRef.current.scrollTo({ top: 0 });
      } else {
        mainRef.current.scrollTop = 0;
      }
    }
  };

  const showHeaderFilter = activeTab === 0 && isScrolled && totalCount > 1;

  if (loading) {
    return (
      <div className="w-full min-h-[100dvh] bg-[var(--md-sys-color-surface)] flex flex-col items-center justify-center p-6 text-center">
        <md-circular-progress indeterminate></md-circular-progress>
        <p className="mt-4 text-xs font-medium text-[var(--md-sys-color-on-surface-variant)]">
          正在初始化普查系统...
        </p>
      </div>
    );
  }

  if (!student) {
    return <AuthPage />;
  }

  const singleScale = totalCount === 1 ? scales[0] : null;
  const progressValue = singleScale
    ? singleScale.status === 'COMPLETED'
      ? singleScale.questionCount
      : singleScale.answeredCount || 0
    : completedCount;
  const progressMax = singleScale ? singleScale.questionCount || 1 : totalCount || 1;

  return (
    <div className="w-full h-[100dvh] min-h-[100dvh] bg-[var(--md-sys-color-surface)] flex flex-col items-center overflow-hidden">
      {/* MD3 Top Navigation Bar - spans full width on wider screens */}
      <header className="sticky top-0 inset-x-0 w-full bg-[var(--md-sys-color-surface)] z-20 shrink-0">
        {/* Progress bar placed at the top of the top bar with no separation line */}
        <md-linear-progress
          value={progressValue}
          max={progressMax}
          style={{
            width: '100%',
            '--md-linear-progress-track-height': '3px',
            '--md-linear-progress-active-indicator-height': '3px',
            display: 'block',
          } as any}
        />

        <div className="w-full px-4 sm:px-8 py-3 flex items-center justify-between">
          <div className="flex-1 min-w-0 pr-2 overflow-hidden h-10 flex items-center">
            <AnimatePresence mode="wait" initial={false}>
              {showHeaderFilter ? (
                <motion.div
                  key="header-filter"
                  initial={{ y: 20, opacity: 0 }}
                  animate={{ y: 0, opacity: 1 }}
                  exit={{ y: 20, opacity: 0 }}
                  transition={{ duration: 0.2, ease: 'easeOut' }}
                  className="flex items-center"
                >
                  <SegmentedButton
                    items={ASSESSMENT_FILTER_ITEMS}
                    selectedValue={filter}
                    onChange={(val) => setFilter(val as AssessmentFilterType)}
                  />
                </motion.div>
              ) : (
                <motion.div
                  key="header-text"
                  initial={{ y: -20, opacity: 0 }}
                  animate={{ y: 0, opacity: 1 }}
                  exit={{ y: -20, opacity: 0 }}
                  transition={{ duration: 0.2, ease: 'easeOut' }}
                  className="flex flex-col justify-center"
                >
                  <span className="text-[10px] font-semibold uppercase tracking-wider text-[var(--md-sys-color-primary)] block leading-tight">
                    中南大学 · 心理普查
                  </span>
                  <h1 className="text-base sm:text-lg font-bold text-[var(--md-sys-color-on-surface)] leading-none mt-0.5 truncate">
                    {scalesLoading
                      ? '正在获取测评任务...'
                      : isAllCompleted
                        ? '全部测评任务已完成'
                        : singleScale && singleScale.status === 'IN_PROGRESS'
                          ? `已完成 ${singleScale.answeredCount || 0} / ${singleScale.questionCount} 题 (${singleScale.completionPercentage || 0}%)`
                          : singleScale
                            ? '心身健康普查问卷待填报'
                            : `还有 ${remainingCount} 项任务待完成`}
                  </h1>
                </motion.div>
              )}
            </AnimatePresence>
          </div>
          {student && (
            <div className="flex items-center gap-2.5 shrink-0">
              <span className="text-xs font-medium text-[var(--md-sys-color-on-surface-variant)] hidden sm:inline">
                {student.fullName} ({student.studentNumber})
              </span>
              <md-outlined-icon-button
                onClick={toggleTheme}
                aria-label={isDark ? '切换为浅色模式' : '切换为深色模式'}
                title={isDark ? '切换为浅色模式' : '切换为深色模式'}
                className="cursor-pointer"
              >
                <md-icon>{isDark ? 'light_mode' : 'dark_mode'}</md-icon>
              </md-outlined-icon-button>
            </div>
          )}
        </div>
      </header>

      {/* Main Body Content */}
      <main
        ref={mainRef}
        className={`w-full max-w-md flex-1 p-4 sm:p-5 pb-24 overflow-y-auto ${
          activeTab === 0 ? 'pt-0 -mt-[2px]' : 'pt-6 sm:pt-8'
        }`}
        onScroll={(e) => {
          if (activeTab === 0) {
            const top = e.currentTarget.scrollTop;
            if (!isScrolled && top > 35) {
              setIsScrolled(true);
            } else if (isScrolled && top <= 10) {
              setIsScrolled(false);
            }
          }
        }}
      >
        {activeTab === 0 ? (
          <AssessmentsPage />
        ) : activeTab === (isTeacher ? 3 : 2) ? (
          <ProfilePage />
        ) : activeTab === 1 && isTeacher ? (
          <TeacherDispatchPage />
        ) : (
          <ReferralsPage />
        )}
      </main>

      {/* MD3 Bottom Navigation Bar - spans full width on wider screens with zero bottom gap */}
      <nav
        className="fixed inset-x-0 w-full bg-[var(--md-sys-color-surface-container)] z-20 flex justify-center"
        style={{
          bottom: '-2px',
          paddingBottom: 'calc(env(safe-area-inset-bottom, 0px) + 2px)',
        }}
      >
        <md-navigation-bar
          active-index={activeTab}
          className="w-full flex"
          style={{
            '--md-navigation-bar-container-elevation': '0',
            '--md-elevation-level': '0',
            width: '100%',
          } as any}
        >
          <md-navigation-tab
            label="问卷测评"
            onClick={() => handleTabChange(0)}
          >
            <md-icon slot="active-icon" filled style={{ fontVariationSettings: "'FILL' 1" } as any}>assignment</md-icon>
            <md-icon slot="inactive-icon">assignment</md-icon>
          </md-navigation-tab>
          {isTeacher && (
            <md-navigation-tab
              label="发放问卷"
              onClick={() => handleTabChange(2)}
            >
              <md-icon slot="active-icon" filled style={{ fontVariationSettings: "'FILL' 1" } as any}>send</md-icon>
              <md-icon slot="inactive-icon">send</md-icon>
            </md-navigation-tab>
          )}
          <md-navigation-tab
            label="转诊进度"
            onClick={() => handleTabChange(isTeacher ? 2 : 1)}
          >
            <md-icon slot="active-icon" filled style={{ fontVariationSettings: "'FILL' 1" } as any}>medical_information</md-icon>
            <md-icon slot="inactive-icon">medical_information</md-icon>
          </md-navigation-tab>
          <md-navigation-tab
            label="个人中心"
            onClick={() => handleTabChange(isTeacher ? 3 : 2)}
          >
            <md-icon slot="active-icon" filled style={{ fontVariationSettings: "'FILL' 1" } as any}>account_circle</md-icon>
            <md-icon slot="inactive-icon">account_circle</md-icon>
          </md-navigation-tab>
        </md-navigation-bar>
      </nav>
    </div>
  );
}

export function StudentApp() {
  return (
    <ThemeProvider>
      <SnackbarProvider>
        <AuthProvider>
          <AssessmentProvider>
            <AppContent />
          </AssessmentProvider>
        </AuthProvider>
      </SnackbarProvider>
    </ThemeProvider>
  );
}
