import { useSnackbar } from "../contexts/SnackbarContext";
import * as React from 'react';
import { Sidebar } from '../components/layout/Sidebar';
import { Header } from '../components/layout/Header';
import { MainContent } from '../components/layout/MainContent';
import { NavItem } from '../components/layout/NavItem';
import { CanvasHeader } from '../components/layout/CanvasHeader';
import { NotificationsView } from '../components/notifications/NotificationsView';
import { ProfileView } from '../components/profile/ProfileView';
import { StudentsView } from '../components/students/StudentsView';
import { ReferralManagementView } from '../components/records/ReferralManagementView';
import { HighRiskListView } from '../components/records/HighRiskListView';
import { SecurityConsentView } from '../components/security/SecurityConsentView';
import { DetailsPanel, DetailsSection, DetailItem } from '../components/common/DetailsPanel';
import { roleTranslations } from '../utils/roleTranslations';
import { DashboardView } from '../components/dashboard/DashboardView';
import { ProfileDetailsView } from '../components/profile/ProfileDetailsView';
import { StudentDetailsView, STUDENT_DETAILS_TABS } from '../components/students/StudentDetailsView';
import { ReferralDetailsView, REFERRAL_DETAILS_TABS } from '../components/records/ReferralDetailsView';
import { REFERRAL_TYPE_LABELS } from '../config/styleConstants';
import { StaffManagementView } from '../components/staff/StaffManagementView';
import { StaffDetailsView, STAFF_DETAILS_TABS } from '../components/staff/StaffDetailsView';
import { AssessmentCatalogManagementView } from '../components/admin/AssessmentCatalogManagementView';
import { useCreationOverlay } from '../contexts/CreationContext';
import { ReferralCreationForm } from '../components/records/ReferralCreationForm';
import { TertiaryFab } from '../components/common/Buttons';
import { queryClient } from '../utils/queryClient';
import { useQuery } from '@tanstack/react-query';
import { useHeadCouncillorProfileSummary } from '../hooks/useProfileSummary';
import { useAuth } from '../contexts/AuthContext';
import { useNavigation } from '../contexts/NavigationContext';
import { useNotifications } from '../hooks/useNotifications';

import { HEAD_COUNCILLOR_METRICS_CONFIG } from '../config/dashboardConfig';
import { DashboardResponseDto, HeadCouncillorMetricsDto } from '../types';
import { useDashboardActivity } from '../hooks/useDashboardActivity';
import { mapActivityTypeToUI } from '../utils/activityMapper';

export const HeadCouncillorTabs = {
  DASHBOARD: 'Dashboard',
  NOTIFICATIONS: 'Notifications',
  STUDENTS: 'Students',
  HIGH_RISK: 'High Risk',
  STAFF: 'Staff',
  ASSESSMENTS: 'Assessments',
  REFERRAL_MANAGEMENT: 'Referral Management',
  SECURITY: 'Security & Consent',
} as const;

export type HeadCouncillorPageName = typeof HeadCouncillorTabs[keyof typeof HeadCouncillorTabs];

const HEAD_COUNCILLOR_TAB_TITLES: Record<HeadCouncillorPageName, string> = {
  [HeadCouncillorTabs.DASHBOARD]: '控制面板',
  [HeadCouncillorTabs.NOTIFICATIONS]: '通知中心',
  [HeadCouncillorTabs.STUDENTS]: '学生管理',
  [HeadCouncillorTabs.HIGH_RISK]: '高危名单',
  [HeadCouncillorTabs.STAFF]: '人员管理',
  [HeadCouncillorTabs.ASSESSMENTS]: '测评量表',
  [HeadCouncillorTabs.REFERRAL_MANAGEMENT]: '转诊管理',
  [HeadCouncillorTabs.SECURITY]: '隐私安全'
};

export function HeadCouncillorPage() {
  const [activePage, setActivePage] = React.useState<HeadCouncillorPageName>(HeadCouncillorTabs.DASHBOARD);
  const [selectedItem, setSelectedItem] = React.useState<any>(null);
  const [showProfileDetails, setShowProfileDetails] = React.useState(false);
  const [refreshKey, setRefreshKey] = React.useState(0);
  const [activeTab, setActiveTab] = React.useState('overview');
  const { session } = useAuth();
  const { unreadCount } = useNotifications(session?.token);
  const { lastEvent } = useNavigation();
  const { showSnackbar } = useSnackbar();

  React.useEffect(() => {
    if (!lastEvent) return;
    const tabMatch = Object.values(HeadCouncillorTabs).find(
      (t) => t.toLowerCase() === lastEvent.tab.toLowerCase()
    );
    if (tabMatch) {
      setActivePage(tabMatch as HeadCouncillorPageName);
      
      if (lastEvent.entityId) {
        let isMounted = true;
        const fetchEntity = async () => {
          try {
            let endpoint = '';
            if (tabMatch === HeadCouncillorTabs.STUDENTS) {
              endpoint = `/api/students/${lastEvent.entityId}`;
            } else if (tabMatch === HeadCouncillorTabs.REFERRAL_MANAGEMENT) {
              endpoint = `/api/referrals/${lastEvent.entityId}`;
            }
            
            if (endpoint) {
              const queryKey = [endpoint, lastEvent.entityId, 'details'];
              const data = await queryClient.fetchQuery<Record<string, unknown>>({
                queryKey,
                queryFn: async ({ signal }) => {
                  const url = `${import.meta.env.BASE_URL}${endpoint.substring(1)}`.replace('//api', '/api');
                  const res = await fetch(url, {
                    signal,
                    headers: { 'Authorization': `Bearer ${session?.token || ''}` }
                  });
                  if (!res.ok) throw new Error('Failed to fetch entity for selection');
                  return res.json();
                }
              });
              
              if (isMounted) {
                setActiveTab('overview');
                setSelectedItem(data.baseInfo ? data.baseInfo : data);
              }
            } else {
              if (isMounted) setSelectedItem(null);
            }
          } catch (e: unknown) {
            if (isMounted) {
              if (e instanceof Error && e.name === 'AbortError') return;
              console.error('Error fetching entity', e);
              showSnackbar({ message: '无法加载数据详情，请重试' });
              setSelectedItem(null);
            }
          }
        };
        fetchEntity();
        return () => {
          isMounted = false;
        };
      } else {
        setSelectedItem(null);
      }
    }
  }, [lastEvent, session?.token, showSnackbar]);
  
  const { data: dashboardData, isLoading: dashboardLoading } = useQuery<DashboardResponseDto<HeadCouncillorMetricsDto>>({
    queryKey: ['/api/dashboard/head-councillor'],
    queryFn: async () => {
      const res = await fetch(`${import.meta.env.BASE_URL}/api/dashboard/head-councillor`.replace('//api', '/api'), {
        headers: {
          'Authorization': `Bearer ${session?.token || ''}`
        }
      });
      if (!res.ok) throw new Error('Failed to fetch dashboard');
      return res.json();
    },
    enabled: activePage === HeadCouncillorTabs.DASHBOARD && !!session?.token
  });
  const { data: activityData } = useDashboardActivity();
  const { openCreation, closeCreation, expandToFullscreen } = useCreationOverlay();
  const { data: profileSummaryData, isLoading: isProfileLoading, isError: isProfileError, refetch: refetchProfile } = useHeadCouncillorProfileSummary(session?.token);




  const handlePageChange = (page: HeadCouncillorPageName) => {
    setActivePage(page);
    setSelectedItem(null);
  };

  const handleViewReferral = (referralId: string) => {
    setActivePage(HeadCouncillorTabs.REFERRAL_MANAGEMENT);
    setSelectedItem({ id: referralId });
  };

  const handleCompose = () => {
    openCreation(
      '发起转诊',
      <ReferralCreationForm onClose={closeCreation} />
    );
    // Expand to fullscreen immediately after opening to accommodate dense structure per request
    setTimeout(expandToFullscreen, 10);
  };

  const composeButton = (
    <TertiaryFab
      icon="edit"
      label="发起转诊"
      onClick={handleCompose}
    />
  );


  // Define tabs configuration for different detail views
  const getTabsForPage = () => {
    switch (activePage) {
      case HeadCouncillorTabs.STUDENTS:
        return STUDENT_DETAILS_TABS;
      case HeadCouncillorTabs.REFERRAL_MANAGEMENT:
        return REFERRAL_DETAILS_TABS;
      case HeadCouncillorTabs.STAFF:
        return STAFF_DETAILS_TABS;
      default:
        return [];
    }
  };

  const tabs = getTabsForPage();

  // Ensure activeTab is always valid for the current set of tabs
  React.useEffect(() => {
    if (tabs.length > 0 && !tabs.find(t => t.id === activeTab)) {
      setActiveTab(tabs[0].id);
    }
  }, [tabs, activeTab]);

  const renderActiveContent = () => {
    switch (activePage) {
      case HeadCouncillorTabs.NOTIFICATIONS:
        return (
          <>
            <CanvasHeader title={HEAD_COUNCILLOR_TAB_TITLES[activePage]} />
            <NotificationsView onViewReferral={handleViewReferral} />
          </>
        );
      case HeadCouncillorTabs.STUDENTS:
        return <StudentsView resetToken={lastEvent?.timestamp} onStudentSelect={setSelectedItem} selectedStudentId={selectedItem?.id} header={(loading) => <CanvasHeader title={HEAD_COUNCILLOR_TAB_TITLES[activePage]} isLoading={loading} />} />;
      case HeadCouncillorTabs.HIGH_RISK:
        return <HighRiskListView header={(loading) => <CanvasHeader title={HEAD_COUNCILLOR_TAB_TITLES[activePage]} isLoading={loading} />} />;
      case HeadCouncillorTabs.STAFF:
        return (
          <>
            <CanvasHeader title={HEAD_COUNCILLOR_TAB_TITLES[activePage]} />
            <StaffManagementView resetToken={lastEvent?.timestamp} onStaffSelect={setSelectedItem} selectedStaffId={selectedItem?.id} />
          </>
        );
      case HeadCouncillorTabs.ASSESSMENTS:
        return (
          <>
            <CanvasHeader title={HEAD_COUNCILLOR_TAB_TITLES[activePage]} />
            <AssessmentCatalogManagementView />
          </>
        );
      case HeadCouncillorTabs.REFERRAL_MANAGEMENT:
        return <ReferralManagementView resetToken={lastEvent?.timestamp} key={`rmv-${refreshKey}`} onReferralSelect={setSelectedItem} selectedReferralId={selectedItem?.id} header={(loading) => <CanvasHeader title={HEAD_COUNCILLOR_TAB_TITLES[activePage]} isLoading={loading} />} />;
      case HeadCouncillorTabs.SECURITY:
        return (
          <>
            <CanvasHeader title={HEAD_COUNCILLOR_TAB_TITLES[activePage]} />
            <SecurityConsentView />
          </>
        );
      case HeadCouncillorTabs.DASHBOARD:
        if (dashboardLoading && !dashboardData) {
          return (
            <>
              <CanvasHeader title={HEAD_COUNCILLOR_TAB_TITLES[activePage]} isLoading={true} />
              <div className="flex-1 flex flex-col items-center justify-center min-h-[200px]">
                {/* @ts-ignore */}
                <md-circular-progress indeterminate></md-circular-progress>
              </div>
            </>
          );
        }
        return dashboardData ? (
          <>
            <CanvasHeader title={HEAD_COUNCILLOR_TAB_TITLES[activePage]} isLoading={dashboardLoading} />
            <DashboardView
              isProfileLoading={isProfileLoading}
              isProfileError={isProfileError}
              onProfileRetry={refetchProfile}
              onProfileClick={() => setShowProfileDetails(true)}
              profileSummary={profileSummaryData ? {
                avatarUrl: profileSummaryData.avatarUrl,
                name: profileSummaryData.name,
                role: roleTranslations[profileSummaryData.role] || profileSummaryData.role,
                metadata: [
                  { icon: "badge", value: profileSummaryData.employeeId || "" },
                  { icon: "account_balance", value: profileSummaryData.department || "" }
                ]
              } : undefined}
            actionMetrics={HEAD_COUNCILLOR_METRICS_CONFIG.map((metric) => ({
              icon: metric.icon,
              numericValue: dashboardData.metrics[metric.metricKey as keyof HeadCouncillorMetricsDto] || 0,
              label: metric.label,
              containerColorClass: metric.containerColorClass,
              onClick: () => handlePageChange(metric.targetPage as HeadCouncillorPageName)
            }))}
            activityTitle="近期动态"
            activities={activityData?.activities?.map(a => {
              const ui = mapActivityTypeToUI(a);
              return {
                id: ui.id,
                title: ui.title,
                timestamp: new Date(ui.timestamp).toLocaleString(),
                statusText: ui.statusText,
                statusType: ui.statusType
              };
            }) ?? []}
          />
        </>) : null;
      default:
        return (
          <div className="flex-1 flex items-center justify-center text-[var(--md-sys-color-on-surface-variant)] pt-20">
            请从侧边栏选择一项
          </div>
        );
    }
  };

  return (
    <div
      className="flex h-screen overflow-hidden transition-colors duration-300"
      style={{
        backgroundColor: 'var(--md-sys-color-surface-container)',
        color: 'var(--md-sys-color-on-background)',
        fontFamily: "'Roboto', sans-serif"
      }}
    >
      <Sidebar composeButton={composeButton}>
        <NavItem icon="dashboard" label="控制面板" active={activePage === HeadCouncillorTabs.DASHBOARD} onClick={() => handlePageChange(HeadCouncillorTabs.DASHBOARD)} />
        <NavItem icon="notifications" label="通知中心" active={activePage === HeadCouncillorTabs.NOTIFICATIONS} onClick={() => handlePageChange(HeadCouncillorTabs.NOTIFICATIONS)} badge={unreadCount > 0} />

        <NavItem icon="group" label="学生管理" active={activePage === HeadCouncillorTabs.STUDENTS} onClick={() => handlePageChange(HeadCouncillorTabs.STUDENTS)} />
        <NavItem icon="warning" label="高危名单" active={activePage === HeadCouncillorTabs.HIGH_RISK} onClick={() => handlePageChange(HeadCouncillorTabs.HIGH_RISK)} />
        <NavItem icon="engineering" label="人员管理" active={activePage === HeadCouncillorTabs.STAFF} onClick={() => handlePageChange(HeadCouncillorTabs.STAFF)} />
        <NavItem icon="fact_check" label="测评量表" active={activePage === HeadCouncillorTabs.ASSESSMENTS} onClick={() => handlePageChange(HeadCouncillorTabs.ASSESSMENTS)} />
        <NavItem icon="assignment_turned_in" label="转诊管理" active={activePage === HeadCouncillorTabs.REFERRAL_MANAGEMENT} onClick={() => handlePageChange(HeadCouncillorTabs.REFERRAL_MANAGEMENT)} />
        {/* <NavItem icon="security" label="隐私安全" active={activePage === HeadCouncillorTabs.SECURITY} onClick={() => handlePageChange(HeadCouncillorTabs.SECURITY)} /> */}
      </Sidebar>

      <div className="flex-1 flex flex-col min-w-0 bg-transparent">
        <Header searchPlaceholder="搜索学生与转诊" onProfileClick={() => setShowProfileDetails(true)} />
        <MainContent
          isSidePanelOpen={!!selectedItem}
          sidePanel={
            <DetailsPanel
              isOpen={!!selectedItem}
              onClose={() => setSelectedItem(null)}
              title={selectedItem?.employeeId ? '人员详情' : (selectedItem?.name ? '学生详情' : (selectedItem?.type ? (REFERRAL_TYPE_LABELS[selectedItem.type] || selectedItem.type) : '转诊详情'))}
              subtitle={selectedItem?.studentNumber || (selectedItem?.studentId ? String(selectedItem.studentId) : '') || (selectedItem?.employeeId ? selectedItem?.department : '') || (selectedItem?.type ? REFERRAL_TYPE_LABELS[selectedItem.type] || selectedItem.type : '') || selectedItem?.major || selectedItem?.department || ''}
              headerAvatar={
                (selectedItem?.name || selectedItem?.studentName) ? (
                  <div className="w-8 h-8 rounded-full bg-[var(--md-sys-color-primary-container)] text-[var(--md-sys-color-on-primary-container)] flex items-center justify-center text-[14px] font-medium">
                    {(selectedItem?.name || selectedItem?.studentName || '?').charAt(0)}
                  </div>
                ) : null
              }
              icon={selectedItem?.name ? 'person' : 'description'}
              tabs={tabs}
              activeTab={activeTab}
              onTabChange={setActiveTab}
            >
              {selectedItem && (
                <>
                  {activePage === HeadCouncillorTabs.STUDENTS && (
                    <StudentDetailsView student={selectedItem} activeTab={activeTab} onTabChange={setActiveTab} />
                  )}
                  {activePage === HeadCouncillorTabs.STAFF && (
                    <StaffDetailsView staff={selectedItem} activeTab={activeTab} onTabChange={setActiveTab} />
                  )}
                  {activePage === HeadCouncillorTabs.REFERRAL_MANAGEMENT && (
                    <ReferralDetailsView 
                      referral={selectedItem} 
                      userRole="head-councillor" 
                      activeTab={activeTab} 
                      onTabChange={setActiveTab} 
                      onUpdate={() => queryClient.invalidateQueries({ queryKey: ['/api/referrals'] })}
                    />
                  )}
                </>
              )}
            </DetailsPanel>
          }>
          <div className="flex-1 min-h-0 flex flex-col relative w-full h-full">
            {renderActiveContent()}
          </div>
        </MainContent>
      </div>

      <ProfileDetailsView
        isOpen={showProfileDetails}
        onBack={() => setShowProfileDetails(false)}
      />
    </div>
  );
}
