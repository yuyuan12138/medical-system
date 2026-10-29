import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { AuthPage } from './AuthPage';
import { AuthProvider } from '../contexts/AuthContext';
import { ThemeProvider } from '../contexts/ThemeContext';
import { intakeApi } from '../api/intakeApi';

// Helper to simulate input on Material Web custom elements in jsdom
function simulateInput(element: Element, value: string) {
  act(() => {
    (element as any).value = value;
    fireEvent(element, new Event('input', { bubbles: true, composed: true }));
  });
}

describe('AuthPage', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('renders Google-style login screen by default with CSU branding', () => {
    render(
      <AuthProvider>
        <AuthPage />
      </AuthProvider>
    );

    // CSU Brand Header & Title
    expect(screen.getByText('CSU')).toBeDefined();
    expect(screen.getByText('登录')).toBeDefined();
    expect(screen.getByText('使用您的中南大学学号以继续心理普查')).toBeDefined();

    // No unnecessary subtitles
    expect(screen.queryByText(/仅需30秒/)).toBeNull();

    // Login inputs
    expect(document.querySelector('md-outlined-text-field[label="学号 / 工号"]')).not.toBeNull();
    expect(document.querySelector('md-outlined-text-field[label="登录密码"]')).not.toBeNull();
    expect(screen.getByText('忘记了学号或密码？')).toBeDefined();

    // Google-style Action Buttons (Left: Create account, Right: Next)
    expect(screen.getByText('创建账号')).toBeDefined();
    expect(screen.getByText('下一步')).toBeDefined();

    // No bottom language / policy footer
    expect(screen.queryByText('中文（简体）')).toBeNull();
    expect(screen.queryByText('隐私权')).toBeNull();
  });

  it('validates empty login inputs on submit', async () => {
    render(
      <AuthProvider>
        <AuthPage />
      </AuthProvider>
    );

    const nextBtn = screen.getByText('下一步');
    fireEvent.click(nextBtn);

    expect(document.querySelector('md-outlined-text-field[error-text="请输入您的学号"]')).not.toBeNull();
    expect(document.querySelector('md-outlined-text-field[error-text="请输入密码"]')).not.toBeNull();
  });

  it('navigates to register step 1 and validates empty inputs', async () => {
    render(
      <AuthProvider>
        <AuthPage />
      </AuthProvider>
    );

    // Click "创建账号" to enter registration sequence
    fireEvent.click(screen.getByText('创建账号'));
    await waitFor(() => {
      expect(screen.getByText('输入您的基本信息以建立新生档案')).toBeDefined();
    });

    // Step 1 Header
    expect(screen.getByText('创建账号')).toBeDefined();
    expect(document.querySelector('md-outlined-text-field[label="真实姓名"]')).not.toBeNull();
    expect(document.querySelector('md-outlined-text-field[label="学号"]')).not.toBeNull();
    expect(document.querySelector('md-outlined-text-field[label="手机号码"]')).not.toBeNull();

    // Attempt next with empty fields
    const nextBtn = screen.getByText('下一步');
    fireEvent.click(nextBtn);

    expect(document.querySelector('md-outlined-text-field[error-text="请输入您的真实姓名"]')).not.toBeNull();
    expect(document.querySelector('md-outlined-text-field[error-text="请输入您的学号"]')).not.toBeNull();
    expect(document.querySelector('md-outlined-text-field[error-text="请输入正确的11位手机号码"]')).not.toBeNull();
  });

  it('progresses to step 2, shows entered phone number, and supports modifying phone', async () => {
    const sendCodeSpy = vi.spyOn(intakeApi, 'sendCode').mockResolvedValue({
      phone: '13800138000',
      devCode: '123456',
      expiresInSeconds: 60,
    });

    render(
      <AuthProvider>
        <AuthPage />
      </AuthProvider>
    );

    fireEvent.click(screen.getByText('创建账号'));
    await waitFor(() => {
      expect(screen.getByText('输入您的基本信息以建立新生档案')).toBeDefined();
    });

    // Fill Step 1
    const nameField = document.querySelector('md-outlined-text-field[label="真实姓名"]')!;
    const numberField = document.querySelector('md-outlined-text-field[label="学号"]')!;
    const phoneField = document.querySelector('md-outlined-text-field[label="手机号码"]')!;

    simulateInput(nameField, '李四');
    simulateInput(numberField, '2026002001');
    simulateInput(phoneField, '13800138000');

    fireEvent.click(screen.getByText('下一步'));

    await waitFor(() => {
      expect(sendCodeSpy).toHaveBeenCalledWith('13800138000', 'REGISTRATION', '2026002001');
    });

    // Step 2 Screen
    await waitFor(() => {
      expect(screen.getByText('验证手机号码')).toBeDefined();
    });
    expect(screen.getByText('13800138000')).toBeDefined();
    expect(document.querySelector('md-outlined-text-field[label="6 位短信验证码"]')).not.toBeNull();

    // Click "修改号码"
    fireEvent.click(screen.getByText('修改号码'));

    // Should return to Step 1 with preserved values
    await waitFor(() => {
      expect(screen.getByText('输入您的基本信息以建立新生档案')).toBeDefined();
    });
    expect(document.querySelector('md-outlined-text-field[label="真实姓名"]')?.getAttribute('value')).toBe('李四');
  });

  it('progresses through step 2 to step 3 and validates password matching', async () => {
    vi.spyOn(intakeApi, 'sendCode').mockResolvedValue({
      phone: '13900139000',
      devCode: '123456',
      expiresInSeconds: 60,
    });
    const verifyCodeSpy = vi.spyOn(intakeApi, 'verifyCode').mockResolvedValue({
      valid: true,
    });

    render(
      <AuthProvider>
        <AuthPage />
      </AuthProvider>
    );

    fireEvent.click(screen.getByText('创建账号'));
    await waitFor(() => {
      expect(screen.getByText('输入您的基本信息以建立新生档案')).toBeDefined();
    });

    // Step 1
    simulateInput(document.querySelector('md-outlined-text-field[label="真实姓名"]')!, '王五');
    simulateInput(document.querySelector('md-outlined-text-field[label="学号"]')!, '2026003001');
    simulateInput(document.querySelector('md-outlined-text-field[label="手机号码"]')!, '13900139000');
    fireEvent.click(screen.getByText('下一步'));

    // Step 2
    await waitFor(() => {
      expect(screen.getByText('验证手机号码')).toBeDefined();
    });

    const codeField = document.querySelector('md-outlined-text-field[label="6 位短信验证码"]')!;
    simulateInput(codeField, '123456');
    fireEvent.click(screen.getByText('下一步'));

    await waitFor(() => {
      expect(verifyCodeSpy).toHaveBeenCalledWith('13900139000', '123456', 'REGISTRATION');
    });

    // Step 3 Screen
    await waitFor(() => {
      expect(screen.getByText('创建安全密码')).toBeDefined();
    });

    const pwdField = document.querySelector('md-outlined-text-field[label="登录密码"]')!;
    const confirmPwdField = document.querySelector('md-outlined-text-field[label="确认密码"]')!;

    // Test mismatched passwords
    simulateInput(pwdField, 'abcdef');
    simulateInput(confirmPwdField, 'xyz123');
    fireEvent.click(screen.getByText('完成'));

    expect(document.querySelector('md-outlined-text-field[error-text="两次输入的密码不一致"]')).not.toBeNull();
  });

  it('supports back navigation through "改为登录" and "上一步"', async () => {
    vi.spyOn(intakeApi, 'sendCode').mockResolvedValue({
      phone: '13812345678',
      devCode: '123456',
      expiresInSeconds: 60,
    });

    render(
      <AuthProvider>
        <AuthPage />
      </AuthProvider>
    );

    // From Login -> Step 1
    fireEvent.click(screen.getByText('创建账号'));
    await waitFor(() => {
      expect(screen.getByText('输入您的基本信息以建立新生档案')).toBeDefined();
    });

    // From Step 1 -> "改为登录" back to Login
    fireEvent.click(screen.getByText('改为登录'));
    await waitFor(() => {
      expect(screen.getByText('使用您的中南大学学号以继续心理普查')).toBeDefined();
    });
  });

  it('translates "Invalid credentials" and displays it in a bottom snackbar instead of inline line', async () => {
    vi.spyOn(intakeApi, 'login').mockRejectedValue(new Error('Invalid credentials'));

    render(
      <AuthProvider>
        <AuthPage />
      </AuthProvider>
    );

    const numField = document.querySelector('md-outlined-text-field[label="学号 / 工号"]')!;
    const pwdField = document.querySelector('md-outlined-text-field[label="登录密码"]')!;

    simulateInput(numField, '8209220532');
    simulateInput(pwdField, 'wrongPassword');

    const nextBtn = screen.getByText('下一步');
    fireEvent.click(nextBtn);

    // Verify snackbar is rendered with translated Chinese message
    await waitFor(() => {
      expect(screen.getByText('学号或密码错误，请重新输入')).toBeDefined();
    });

    // Verify there is NO inline error banner inside the login form
    expect(screen.queryByText('Invalid credentials')).toBeNull();
    const formErrorContainers = document.querySelector('form')?.querySelectorAll('.bg-\\[var\\(--md-sys-color-error-container\\)\\]');
    expect(formErrorContainers?.length ?? 0).toBe(0);

    // Verify snackbar dismiss button
    const closeBtn = screen.getByText('关闭');
    expect(closeBtn).toBeDefined();
    fireEvent.click(closeBtn);
  });

  it('rejects formula injection prefix in login student number', async () => {
    render(
      <AuthProvider>
        <AuthPage />
      </AuthProvider>
    );

    const numField = document.querySelector('md-outlined-text-field[label="学号 / 工号"]')!;
    const pwdField = document.querySelector('md-outlined-text-field[label="登录密码"]')!;

    simulateInput(numField, '=CMD()');
    simulateInput(pwdField, 'password123');

    fireEvent.click(screen.getByText('下一步'));

    expect(
      document.querySelector('md-outlined-text-field[error-text="学号不能包含公式或特殊计算符号"]')
    ).not.toBeNull();
  });

  it('rejects invalid mobile carrier prefix and malformed student number in step 1', async () => {
    render(
      <AuthProvider>
        <AuthPage />
      </AuthProvider>
    );

    fireEvent.click(screen.getByText('创建账号'));
    await waitFor(() => {
      expect(screen.getByText('输入您的基本信息以建立新生档案')).toBeDefined();
    });

    const nameField = document.querySelector('md-outlined-text-field[label="真实姓名"]')!;
    const numField = document.querySelector('md-outlined-text-field[label="学号"]')!;
    const phoneField = document.querySelector('md-outlined-text-field[label="手机号码"]')!;

    simulateInput(nameField, '张三');
    simulateInput(numField, '12'); // too short
    simulateInput(phoneField, '10812345678'); // invalid carrier

    fireEvent.click(screen.getByText('下一步'));

    expect(
      document.querySelector('md-outlined-text-field[error-text="学号格式不正确（本科生10位数字、研究生9位数字，留学生以L开头加9位数字，首位非0）"]')
    ).not.toBeNull();
    expect(
      document.querySelector('md-outlined-text-field[error-text="请输入正确的11位中国大陆手机号码"]')
    ).not.toBeNull();
  });

  it('normalizes lowercase l in international student number and accepts it', async () => {
    const loginSpy = vi.spyOn(intakeApi, 'login').mockResolvedValue({
      token: 'jwt-token',
      student: {
        studentNumber: 'L209220532',
        fullName: 'John Doe',
        phone: '13900112233',
        registeredAt: '2026-09-01T10:00:00',
      },
    });

    render(
      <AuthProvider>
        <AuthPage />
      </AuthProvider>
    );

    const numField = document.querySelector('md-outlined-text-field[label="学号 / 工号"]')!;
    const pwdField = document.querySelector('md-outlined-text-field[label="登录密码"]')!;

    simulateInput(numField, '  l209220532  ');
    simulateInput(pwdField, 'secret123');

    fireEvent.click(screen.getByText('下一步'));

    await waitFor(() => {
      expect(loginSpy).toHaveBeenCalledWith({
        identifier: 'L209220532',
        studentNumber: 'L209220532',
        password: 'secret123',
      });
    });
  });

  it('rejects degenerate uniform repeating student numbers', async () => {
    render(
      <AuthProvider>
        <AuthPage />
      </AuthProvider>
    );

    const numField = document.querySelector('md-outlined-text-field[label="学号 / 工号"]')!;
    const pwdField = document.querySelector('md-outlined-text-field[label="登录密码"]')!;

    simulateInput(numField, '9999999999');
    simulateInput(pwdField, 'secret123');

    fireEvent.click(screen.getByText('下一步'));

    expect(
      document.querySelector('md-outlined-text-field[error-text="学号不能为全重复数字"]')
    ).not.toBeNull();
  });

  it('switches to SMS login mode, validates phone, sends code with LOGIN purpose, and logs in', async () => {
    const sendCodeSpy = vi.spyOn(intakeApi, 'sendCode').mockResolvedValue({
      phone: '13812345678',
      expiresInSeconds: 60,
    });
    const loginWithSmsSpy = vi.spyOn(intakeApi, 'loginWithSms').mockResolvedValue({
      token: 'jwt-sms-token',
      student: {
        studentNumber: '8209220001',
        fullName: '张三',
        phone: '13812345678',
        registeredAt: new Date().toISOString(),
      },
    });

    render(
      <AuthProvider>
        <AuthPage />
      </AuthProvider>
    );

    // Click on "手机验证码" segmented button
    fireEvent.click(screen.getByText('手机验证码'));

    // Verify SMS login fields are displayed
    expect(screen.getByText('输入已登记手机号码及短信验证码快捷登录')).toBeDefined();
    const phoneField = document.querySelector('md-outlined-text-field[label="手机号码"]')!;
    const codeField = document.querySelector('md-outlined-text-field[label="6 位验证码"]')!;
    expect(phoneField).not.toBeNull();
    expect(codeField).not.toBeNull();

    // Trigger send code with invalid phone
    fireEvent.click(screen.getByText('获取验证码'));
    expect(document.querySelector('md-outlined-text-field[error-text="请输入正确的11位手机号码"]')).not.toBeNull();

    // Enter valid phone and send code
    simulateInput(phoneField, '13812345678');
    fireEvent.click(screen.getByText('获取验证码'));

    await waitFor(() => {
      expect(sendCodeSpy).toHaveBeenCalledWith('13812345678', 'LOGIN');
    });

    // Enter 6-digit code and submit
    simulateInput(codeField, '654321');
    fireEvent.click(screen.getByText('下一步'));

    await waitFor(() => {
      expect(loginWithSmsSpy).toHaveBeenCalledWith('13812345678', '654321');
    });
  });

  it('displays error snackbar with action and redirects to password login when student number is already registered', async () => {
    vi.spyOn(intakeApi, 'sendCode').mockRejectedValue(new Error('该学号已被注册: 2026001001'));

    render(
      <AuthProvider>
        <AuthPage />
      </AuthProvider>
    );

    fireEvent.click(screen.getByText('创建账号'));
    await waitFor(() => {
      expect(screen.getByText('输入您的基本信息以建立新生档案')).toBeDefined();
    });

    simulateInput(document.querySelector('md-outlined-text-field[label="真实姓名"]')!, '张三');
    simulateInput(document.querySelector('md-outlined-text-field[label="学号"]')!, '2026001001');
    simulateInput(document.querySelector('md-outlined-text-field[label="手机号码"]')!, '13800112233');

    fireEvent.click(screen.getByText('下一步'));

    await waitFor(() => {
      const snackbar = screen.getByTestId('snackbar');
      expect(snackbar).toBeDefined();
      expect(snackbar.className).toContain('bg-[var(--md-sys-color-error-container)]');
      expect(screen.getByText('前往登录')).toBeDefined();
    });

    // Click snackbar redirect action button
    fireEvent.click(screen.getByText('前往登录'));

    // Should switch to password login with student number prefilled
    await waitFor(() => {
      expect(screen.getByText('使用您的中南大学学号以继续心理普查')).toBeDefined();
    });
    expect(
      document.querySelector('md-outlined-text-field[label="学号 / 工号"]')?.getAttribute('value')
    ).toBe('2026001001');
  });

  it('displays error snackbar with action and redirects to sms login when phone number is already registered', async () => {
    vi.spyOn(intakeApi, 'sendCode').mockRejectedValue(new Error('该手机号码已被注册: 13800112233'));

    render(
      <AuthProvider>
        <AuthPage />
      </AuthProvider>
    );

    fireEvent.click(screen.getByText('创建账号'));
    await waitFor(() => {
      expect(screen.getByText('输入您的基本信息以建立新生档案')).toBeDefined();
    });

    simulateInput(document.querySelector('md-outlined-text-field[label="真实姓名"]')!, '张三');
    simulateInput(document.querySelector('md-outlined-text-field[label="学号"]')!, '2026001001');
    simulateInput(document.querySelector('md-outlined-text-field[label="手机号码"]')!, '13800112233');

    fireEvent.click(screen.getByText('下一步'));

    await waitFor(() => {
      const snackbar = screen.getByTestId('snackbar');
      expect(snackbar).toBeDefined();
      expect(snackbar.className).toContain('bg-[var(--md-sys-color-error-container)]');
      expect(screen.getByText('前往登录')).toBeDefined();
    });

    // Click snackbar redirect action button
    fireEvent.click(screen.getByText('前往登录'));

    // Should switch to SMS login with phone number prefilled
    await waitFor(() => {
      expect(screen.getByText('输入已登记手机号码及短信验证码快捷登录')).toBeDefined();
    });
    expect(
      document.querySelector('md-outlined-text-field[label="手机号码"]')?.getAttribute('value')
    ).toBe('13800112233');
  });

  it('renders indeterminate linear progress bar and locks inputs and buttons during network requests with static button copy', async () => {
    let resolveLogin: (val: any) => void;
    const loginPromise = new Promise((resolve) => {
      resolveLogin = resolve;
    });
    vi.spyOn(intakeApi, 'login').mockReturnValue(loginPromise as any);

    render(
      <AuthProvider>
        <AuthPage />
      </AuthProvider>
    );

    const numField = document.querySelector('md-outlined-text-field[label="学号 / 工号"]')!;
    const pwdField = document.querySelector('md-outlined-text-field[label="登录密码"]')!;

    simulateInput(numField, '8209220532');
    simulateInput(pwdField, 'password123');

    // Before submit: no progress bar, fields enabled
    expect(document.querySelector('md-linear-progress')).toBeNull();
    expect(numField.hasAttribute('disabled')).toBe(false);

    // Submit
    const nextBtn = screen.getByText('下一步');
    fireEvent.click(nextBtn);

    // During network request: linear progress is visible
    await waitFor(() => {
      expect(document.querySelector('md-linear-progress')).not.toBeNull();
    });

    // Inputs and buttons must be disabled
    expect(numField.hasAttribute('disabled')).toBe(true);
    expect(pwdField.hasAttribute('disabled')).toBe(true);
    expect(nextBtn.closest('md-filled-button')?.hasAttribute('disabled')).toBe(true);

    // Button label MUST remain static ("下一步", not "登录中..." or "加载中...")
    expect(screen.getByText('下一步')).toBeDefined();

    // Resolve network call
    await act(async () => {
      resolveLogin({
        token: 'token-123',
        student: { studentNumber: '8209220532', fullName: '测试', phone: '13800000000', registeredAt: '' },
      });
    });

    // After completion, progress bar is removed
    await waitFor(() => {
      expect(document.querySelector('md-linear-progress')).toBeNull();
    });
  });

  it('renders verification code button directly inside trailing-icon slot of text field with matching height', async () => {
    render(
      <AuthProvider>
        <AuthPage />
      </AuthProvider>
    );

    // Switch to SMS login
    fireEvent.click(screen.getByText('手机验证码'));

    const smsCodeField = document.querySelector('md-outlined-text-field[label="6 位验证码"]')!;
    expect(smsCodeField).not.toBeNull();

    // Verify trailing-icon slot button exists inside the text field
    const trailingBtn = smsCodeField.querySelector('button[slot="trailing-icon"]') as HTMLButtonElement;
    expect(trailingBtn).not.toBeNull();
    expect(trailingBtn?.textContent).toContain('获取验证码');

    // Verify the trailing button has matching height class (h-10 min-h-[40px])
    expect(trailingBtn.className).toContain('h-10');
    expect(trailingBtn.className).toContain('min-h-[40px]');

    // Verify field does NOT blow up with 84px trailing icon size
    expect(smsCodeField.getAttribute('style') || '').not.toContain('84px');

    // Verify trailing padding matches input field start padding
    expect(smsCodeField.getAttribute('style')).toContain('--md-outlined-text-field-with-trailing-icon-trailing-space: 6px');
    expect(trailingBtn.className).toContain('px-2.5');
  });

  it('renders Google-style theme switcher with Material Design menu and toggles between light and dark modes', async () => {
    render(
      <ThemeProvider>
        <AuthProvider>
          <AuthPage />
        </AuthProvider>
      </ThemeProvider>
    );

    const themeButton = screen.getByLabelText('选择界面外观主题');
    expect(themeButton).toBeDefined();
    expect(themeButton.textContent).toContain('浅色模式');

    const themeMenu = document.querySelector('md-menu#theme-menu');
    expect(themeMenu).not.toBeNull();

    // Verify menu items for light and dark modes exist
    const menuItems = document.querySelectorAll('md-menu-item');
    expect(menuItems.length).toBe(2);

    // Click dark mode menu item
    const darkMenuItem = Array.from(menuItems).find(item => item.textContent?.includes('深色模式'))!;
    expect(darkMenuItem).toBeDefined();
    fireEvent.click(darkMenuItem);

    expect(document.documentElement.classList.contains('dark')).toBe(true);
    expect(localStorage.getItem('app-theme')).toBe('dark');
    expect(themeButton.textContent).toContain('深色模式');

    // Click light mode menu item
    const lightMenuItem = Array.from(menuItems).find(item => item.textContent?.includes('浅色模式'))!;
    fireEvent.click(lightMenuItem);

    expect(document.documentElement.classList.contains('light')).toBe(true);
    expect(localStorage.getItem('app-theme')).toBe('light');
    expect(themeButton.textContent).toContain('浅色模式');

    // Verify university branding is rendered on the same line in the footer
    const footerContainer = themeButton.closest('div.justify-between');
    expect(footerContainer).not.toBeNull();
    expect(footerContainer?.textContent).toContain('中南大学');
    expect(footerContainer?.textContent).toContain('心理普查');
  });

  it('prevents double-click bug by reopening theme menu on a single click after outside dismissal', async () => {
    render(
      <ThemeProvider>
        <AuthProvider>
          <AuthPage />
        </AuthProvider>
      </ThemeProvider>
    );

    const themeButton = screen.getByLabelText('选择界面外观主题');
    const themeMenu = document.querySelector('md-menu#theme-menu') as any;
    expect(themeMenu).not.toBeNull();

    // 1st click: opens menu
    fireEvent.click(themeButton);
    expect(themeButton.getAttribute('aria-expanded')).toBe('true');

    // Simulate menu closing via outside click / native closed event
    fireEvent(themeMenu, new Event('closed'));

    // State should be immediately synchronized to closed
    expect(themeButton.getAttribute('aria-expanded')).toBe('false');

    // Single click after outside dismissal must immediately open the menu (no double-click needed)
    fireEvent.click(themeButton);
    expect(themeButton.getAttribute('aria-expanded')).toBe('true');
  });
});


