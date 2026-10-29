import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import App from './App';
import { intakeApi } from './api/intakeApi';
import { tokenStorage } from './api/intakeApi';

describe('App Top Bar & Navigation', () => {
  const mockStudent = {
    studentNumber: '8209220532',
    fullName: '张同学',
    phone: '13800000000',
    registeredAt: '2026-09-01T08:00:00',
  };

  const mockScales = [
    {
      code: 'demographics_survey',
      title: '个人基本信息核对',
      description: '采集基本信息',
      questionCount: 8,
      estimatedMinutes: 3,
      status: 'NOT_STARTED' as const,
    },
    {
      code: 'phq_9',
      title: 'PHQ-9 抑郁健康问卷',
      description: '日常情绪评估',
      questionCount: 9,
      estimatedMinutes: 2,
      status: 'COMPLETED' as const,
    },
    {
      code: 'gad_7',
      title: 'GAD-7 焦虑筛查问卷',
      description: '焦虑倾向评估',
      questionCount: 7,
      estimatedMinutes: 2,
      status: 'NOT_STARTED' as const,
    },
  ];

  beforeEach(() => {
    localStorage.clear();
    document.documentElement.className = '';
    tokenStorage.set('valid-test-token');
    vi.spyOn(intakeApi, 'getMe').mockResolvedValue(mockStudent);
    vi.spyOn(intakeApi, 'getScales').mockResolvedValue(mockScales);
    vi.spyOn(intakeApi, 'getMyProfile').mockResolvedValue({
      studentNumber: '8209220532',
      fullName: '张同学',
      phone: '13800000000',
      registeredAt: '2026-09-01T08:00:00',
      isDemographicsSubmitted: false,
      demographics: { isSubmitted: false },
    });
  });

  it('displays "还有x项任务待完成" in the top bar header', async () => {
    render(<App />);

    // 1 completed out of 3 -> 2 remaining tasks
    await waitFor(() => {
      expect(screen.getByRole('heading', { level: 1, name: '还有 2 项任务待完成' })).toBeDefined();
    });
  });

  it('renders progress bar at the top of the top bar and no separation line (border-b)', async () => {
    const { container } = render(<App />);

    await waitFor(() => {
      expect(screen.getByRole('heading', { level: 1, name: '还有 2 项任务待完成' })).toBeDefined();
    });

    const header = container.querySelector('header');
    expect(header).toBeDefined();
    // Verify separation line (border-b) is removed
    expect(header?.className).not.toContain('border-b');

    // Verify leading icon at start of top bar is removed
    expect(header?.querySelector('md-icon')?.textContent).not.toBe('psychology');

    // Verify md-linear-progress exists at top of header
    const progressBar = header?.querySelector('md-linear-progress');
    expect(progressBar).not.toBeNull();
    expect(header?.firstElementChild).toBe(progressBar);
    expect(progressBar?.getAttribute('value')).toBe('1');
    expect(progressBar?.getAttribute('max')).toBe('3');
  });

  it('replaces avatar image with light/dark mode outlined icon button and toggles dark theme', async () => {
    const { container } = render(<App />);

    await waitFor(() => {
      expect(screen.getByRole('heading', { level: 1, name: '还有 2 项任务待完成' })).toBeDefined();
    });

    const header = container.querySelector('header');
    // Ensure avatar button is NOT present in the header
    const avatarButton = header?.querySelector('button[title="查看个人中心"]');
    expect(avatarButton).toBeNull();

    // Verify outlined icon button is present
    const themeBtn = header?.querySelector('md-outlined-icon-button');
    expect(themeBtn).not.toBeNull();
    expect(themeBtn?.getAttribute('aria-label')).toBe('切换为深色模式');

    // Click theme button to toggle to dark mode
    fireEvent.click(themeBtn!);

    // Root should have 'dark' class
    expect(document.documentElement.classList.contains('dark')).toBe(true);
    expect(localStorage.getItem('app-theme')).toBe('dark');
    expect(themeBtn?.getAttribute('aria-label')).toBe('切换为浅色模式');

    // Click again to toggle back to light mode
    fireEvent.click(themeBtn!);
    expect(document.documentElement.classList.contains('light')).toBe(true);
    expect(document.documentElement.classList.contains('dark')).toBe(false);
    expect(localStorage.getItem('app-theme')).toBe('light');
  });

  it('replaces header text with segmented button when scrolling down, and restores text when scrolling back to top', async () => {
    const { container } = render(<App />);

    await waitFor(() => {
      expect(screen.getByRole('heading', { level: 1, name: '还有 2 项任务待完成' })).toBeDefined();
    });

    const header = container.querySelector('header');
    const main = container.querySelector('main');
    expect(header).not.toBeNull();
    expect(main).not.toBeNull();

    // At top: header has the title text, not the segmented button
    expect(header?.querySelector('h1')).not.toBeNull();
    expect(header?.querySelector('[role="group"]')).toBeNull();

    // Scroll down past threshold (scrollTop > 35)
    fireEvent.scroll(main!, { target: { scrollTop: 50 } });

    // Header should now show segmented button
    await waitFor(() => {
      expect(header?.querySelector('[role="group"]')).not.toBeNull();
    });
    expect(header?.querySelector('h1')).toBeNull();

    // The segmented button in header should contain filter options
    const headerSegmentedGroup = header?.querySelector('[role="group"]');
    expect(headerSegmentedGroup?.textContent).toContain('全部');
    expect(headerSegmentedGroup?.textContent).toContain('未完成');
    expect(headerSegmentedGroup?.textContent).toContain('已完成');

    // Click '未完成' inside header segmented button
    const unfinishedBtn = Array.from(headerSegmentedGroup!.querySelectorAll('button')).find(
      (btn) => btn.textContent?.includes('未完成')
    );
    expect(unfinishedBtn).toBeDefined();
    fireEvent.click(unfinishedBtn!);

    // Card list should reflect the filtered state (PHQ-9 is completed, so should be filtered out)
    expect(screen.queryByText('PHQ-9 抑郁健康问卷')).toBeNull();
    expect(screen.getByText('个人基本信息核对')).toBeDefined();

    // Scroll back all the way to top (scrollTop <= 10)
    fireEvent.scroll(main!, { target: { scrollTop: 0 } });

    // Header text should be restored, pushing the segmented button back into place
    await waitFor(() => {
      expect(header?.querySelector('h1')).not.toBeNull();
    });
    expect(header?.querySelector('[role="group"]')).toBeNull();
    expect(screen.getByRole('heading', { level: 1, name: '还有 2 项任务待完成' })).toBeDefined();
  });

  it('does not replace header text with segmented button when scrolling on the Profile tab', async () => {
    const { container } = render(<App />);

    await waitFor(() => {
      expect(screen.getByRole('heading', { level: 1, name: '还有 2 项任务待完成' })).toBeDefined();
    });

    // Switch to Tab 1 (个人中心)
    const navTabs = container.querySelectorAll('md-navigation-tab');
    expect(navTabs.length).toBe(3);
    const profileTab = navTabs[1];
    fireEvent.click(profileTab);

    const header = container.querySelector('header');
    const main = container.querySelector('main');

    // Scroll down on profile tab
    fireEvent.scroll(main!, { target: { scrollTop: 100 } });

    // Header should still have heading, never the segmented button
    expect(header?.querySelector('h1')).not.toBeNull();
    expect(header?.querySelector('[role="group"]')).toBeNull();
  });

  it('renders bottom navigation tabs with filled active icons and outlined inactive icons', async () => {
    const { container } = render(<App />);

    await waitFor(() => {
      expect(screen.getByRole('heading', { level: 1, name: '还有 2 项任务待完成' })).toBeDefined();
    });

    const navTabs = container.querySelectorAll('md-navigation-tab');
    expect(navTabs.length).toBe(3);

    navTabs.forEach((tab) => {
      const activeIcon = tab.querySelector('md-icon[slot="active-icon"]');
      const inactiveIcon = tab.querySelector('md-icon[slot="inactive-icon"]');

      expect(activeIcon).not.toBeNull();
      expect(inactiveIcon).not.toBeNull();

      // Active icon must have filled attribute and font-variation-settings for 'FILL' 1
      expect(activeIcon?.hasAttribute('filled')).toBe(true);
      expect((activeIcon as HTMLElement).style.fontVariationSettings).toContain("'FILL' 1");

      // Inactive icon must NOT have filled attribute
      expect(inactiveIcon?.hasAttribute('filled')).toBe(false);
    });
  });
});
