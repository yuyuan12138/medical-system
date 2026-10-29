import React, { useState, useEffect, useRef } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { useAuth } from '../contexts/AuthContext';
import { useTheme } from '../contexts/ThemeContext';
import { intakeApi } from '../api/intakeApi';
import { PrimaryButton, SegmentedButton } from '../components/common/Buttons';
import { Snackbar } from '../components/common/Snackbar';
import {
  validateStudentNumber,
  validateChineseMobile,
  validatePersonName,
} from '../domain/validators';

type AuthView = 'login' | 'register_step1' | 'register_step2' | 'register_step3';
export type LoginMode = 'password' | 'sms';

const VIEW_INDEXES: Record<AuthView, number> = {
  login: 0,
  register_step1: 1,
  register_step2: 2,
  register_step3: 3,
};

const pageVariants = {
  enter: ({ direction, isStepSlide }: { direction: number; isStepSlide: boolean }) => {
    if (!isStepSlide) {
      return { opacity: 0, scale: 0.98, x: 0 };
    }
    return {
      x: direction > 0 ? '100%' : '-100%',
      opacity: 0,
    };
  },
  center: {
    x: 0,
    opacity: 1,
    scale: 1,
  },
  exit: ({ direction, isStepSlide }: { direction: number; isStepSlide: boolean }) => {
    if (!isStepSlide) {
      return { opacity: 0, scale: 0.98, x: 0 };
    }
    return {
      x: direction > 0 ? '-100%' : '100%',
      opacity: 0,
    };
  },
};

const getTransition = (isStepSlide: boolean) =>
  isStepSlide
    ? { duration: 0.28, ease: [0.2, 0, 0, 1] as const }
    : { duration: 0.2, ease: 'easeOut' as const };

export const LOGIN_MODE_ITEMS = [
  { label: '学号密码', value: 'password' },
  { label: '手机验证码', value: 'sms' },
];

export function translateAuthError(msg?: string): string {
  if (!msg) return '登录失败，请核对输入信息';
  const lower = msg.toLowerCase().trim();
  if (
    lower.includes('invalid credential') ||
    lower.includes('invalid_credential') ||
    lower === 'unauthorized' ||
    lower.includes('bad credentials')
  ) {
    return '学号或密码错误，请重新输入';
  }
  if (lower.includes('not found') || lower.includes('student not found') || lower.includes('尚未登记')) {
    return '该账号尚未登记，请先创建账号';
  }
  if (
    lower.includes('手机号码已被注册') ||
    lower.includes('手机号已被注册') ||
    (lower.includes('phone') && (lower.includes('already') || lower.includes('registered') || lower.includes('duplicate')))
  ) {
    return '该手机号码已被注册';
  }
  if (
    lower.includes('学号已被注册') ||
    lower.includes('student number already registered') ||
    lower.includes('already registered') ||
    lower.includes('conflict')
  ) {
    return '该学号已被注册';
  }
  if (lower.includes('频繁') || lower.includes('wait') || lower.includes('cooldown')) {
    return msg;
  }
  if (lower.includes('验证码') || lower.includes('code')) {
    return msg;
  }
  if (lower.includes('network') || lower.includes('failed to fetch')) {
    return '网络连接异常，请稍后重试';
  }
  return msg;
}

export const AuthPage: React.FC = () => {
  const { login, loginWithSms, register } = useAuth();
  const { theme, setTheme } = useTheme();
  const [view, setView] = useState<AuthView>('login');
  const [direction, setDirection] = useState<number>(1);
  const [isStepSlide, setIsStepSlide] = useState<boolean>(false);
  const [loginMode, setLoginMode] = useState<LoginMode>('password');
  const [isThemeMenuOpen, setIsThemeMenuOpen] = useState(false);
  const themeMenuRef = useRef<HTMLElement | null>(null);
  const themeMenuAnchorRef = useRef<HTMLButtonElement | null>(null);

  // Synchronize theme menu open state with native Lit web component events to prevent double-click requirement
  useEffect(() => {
    const menuEl = themeMenuRef.current;
    if (!menuEl) return;

    const handleClosed = () => {
      setIsThemeMenuOpen(false);
    };

    menuEl.addEventListener('closed', handleClosed);
    menuEl.addEventListener('closing', handleClosed);

    return () => {
      menuEl.removeEventListener('closed', handleClosed);
      menuEl.removeEventListener('closing', handleClosed);
    };
  }, []);

  const toggleThemeMenu = (e: React.MouseEvent) => {
    e.stopPropagation();
    const menuEl = themeMenuRef.current as any;
    if (menuEl) {
      if (themeMenuAnchorRef.current) {
        menuEl.anchorElement = themeMenuAnchorRef.current;
      }
      const isCurrentlyOpen = Boolean(menuEl.open);
      if (isCurrentlyOpen) {
        if (typeof menuEl.close === 'function') {
          menuEl.close();
        } else {
          menuEl.open = false;
        }
        setIsThemeMenuOpen(false);
      } else {
        if (typeof menuEl.show === 'function') {
          menuEl.show();
        } else {
          menuEl.open = true;
        }
        setIsThemeMenuOpen(true);
      }
    } else {
      setIsThemeMenuOpen((prev) => !prev);
    }
  };

  const navigateTo = (nextView: AuthView) => {
    const isRegStepChange = view.startsWith('register_') && nextView.startsWith('register_');
    setIsStepSlide(isRegStepChange);
    const fromIdx = VIEW_INDEXES[view];
    const toIdx = VIEW_INDEXES[nextView];
    setDirection(toIdx >= fromIdx ? 1 : -1);
    setView(nextView);
  };

  // Password Login form state
  const [loginStudentNumber, setLoginStudentNumber] = useState('');
  const [loginPassword, setLoginPassword] = useState('');
  const [showLoginPassword, setShowLoginPassword] = useState(false);

  // SMS Login form state
  const [loginPhone, setLoginPhone] = useState('');
  const [loginSmsCode, setLoginSmsCode] = useState('');
  const [loginCountdown, setLoginCountdown] = useState(0);
  const loginCountdownTimerRef = useRef<NodeJS.Timeout | null>(null);

  // Registration wizard state (preserved across back and forward steps)
  const [regFullName, setRegFullName] = useState('');
  const [regStudentNumber, setRegStudentNumber] = useState('');
  const [regPhone, setRegPhone] = useState('');
  const [verificationCode, setVerificationCode] = useState('');
  const [regPassword, setRegPassword] = useState('');
  const [regConfirmPassword, setRegConfirmPassword] = useState('');
  const [showRegPassword, setShowRegPassword] = useState(false);
  const [showRegConfirmPassword, setShowRegConfirmPassword] = useState(false);

  // SMS countdown timer for registration
  const [countdown, setCountdown] = useState(0);
  const countdownTimerRef = useRef<NodeJS.Timeout | null>(null);

  // UI state
  const [loading, setLoading] = useState(false);
  const [snackbarOpen, setSnackbarOpen] = useState(false);
  const [snackbarMessage, setSnackbarMessage] = useState('');
  const [snackbarVariant, setSnackbarVariant] = useState<'default' | 'error'>('error');
  const [snackbarActionLabel, setSnackbarActionLabel] = useState('关闭');
  const [snackbarAction, setSnackbarAction] = useState<(() => void) | undefined>(undefined);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const showAuthError = (rawError?: string, defaultMsg = '操作失败，请重试') => {
    const translated = translateAuthError(rawError || defaultMsg);
    setSnackbarMessage(translated);
    setSnackbarVariant('error');
    if (translated.includes('学号已被注册') || translated.includes('学号已存在')) {
      setSnackbarActionLabel('前往登录');
      setSnackbarAction(() => () => {
        setLoginStudentNumber(regStudentNumber);
        setLoginMode('password');
        navigateTo('login');
        setSnackbarOpen(false);
      });
    } else if (translated.includes('手机号码已被注册') || translated.includes('手机号已存在')) {
      setSnackbarActionLabel('前往登录');
      setSnackbarAction(() => () => {
        setLoginPhone(regPhone);
        setLoginMode('sms');
        navigateTo('login');
        setSnackbarOpen(false);
      });
    } else {
      setSnackbarActionLabel('关闭');
      setSnackbarAction(undefined);
    }
    setSnackbarOpen(true);
  };

  useEffect(() => {
    return () => {
      if (countdownTimerRef.current) {
        clearInterval(countdownTimerRef.current);
      }
      if (loginCountdownTimerRef.current) {
        clearInterval(loginCountdownTimerRef.current);
      }
    };
  }, []);

  const startCountdown = () => {
    setCountdown(60);
    if (countdownTimerRef.current) clearInterval(countdownTimerRef.current);
    countdownTimerRef.current = setInterval(() => {
      setCountdown((prev) => {
        if (prev <= 1) {
          if (countdownTimerRef.current) clearInterval(countdownTimerRef.current);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
  };

  const startLoginCountdown = () => {
    setLoginCountdown(60);
    if (loginCountdownTimerRef.current) clearInterval(loginCountdownTimerRef.current);
    loginCountdownTimerRef.current = setInterval(() => {
      setLoginCountdown((prev) => {
        if (prev <= 1) {
          if (loginCountdownTimerRef.current) clearInterval(loginCountdownTimerRef.current);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
  };

  const clearFieldError = (field: string) => {
    if (fieldErrors[field]) {
      setFieldErrors((prev) => {
        const next = { ...prev };
        delete next[field];
        return next;
      });
    }
  };

  // Send Login SMS verification code
  const handleSendLoginCode = async () => {
    if (loginCountdown > 0 || loading) return;
    setSnackbarOpen(false);
    const phoneValidation = validateChineseMobile(loginPhone);
    if (!phoneValidation.isValid) {
      setFieldErrors((prev) => ({ ...prev, loginPhone: phoneValidation.error! }));
      return;
    }

    setLoading(true);
    try {
      const res: any = await intakeApi.sendCode(phoneValidation.normalized || loginPhone.trim(), 'LOGIN');
      startLoginCountdown();
      if (res?.devCode) {
        setSnackbarMessage(`[开发环境] 验证码: ${res.devCode}`);
        setSnackbarOpen(true);
      }
    } catch (err: any) {
      console.error('Send login code error:', err);
      showAuthError(err.message, '获取验证码失败，请重试');
    } finally {
      setLoading(false);
    }
  };

  // 1. Handle Login Submit (Password or SMS)
  const handleLoginSubmit = async (e?: React.FormEvent) => {
    e?.preventDefault();
    setSnackbarOpen(false);
    const errors: Record<string, string> = {};

    if (loginMode === 'password') {
      const trimmedNum = loginStudentNumber.trim();
      const isLegacySeedNum = /^[Ss]\d{7}$|^\d{7}$/.test(trimmedNum);
      const isStaffEmployeeNumber = /^[A-Za-z]{2,4}-\d{3,8}$/.test(trimmedNum);
      const numValidation = isLegacySeedNum || isStaffEmployeeNumber
        ? { isValid: true, normalized: trimmedNum.toUpperCase() }
        : validateStudentNumber(loginStudentNumber);
      if (!numValidation.isValid) {
        errors.loginStudentNumber = numValidation.error!;
      }
      if (!loginPassword) {
        errors.loginPassword = '请输入密码';
      }

      if (Object.keys(errors).length > 0) {
        setFieldErrors(errors);
        return;
      }

      setLoading(true);
      try {
        const cleanNum = numValidation.normalized || loginStudentNumber.trim();
        await login({
          identifier: cleanNum,
          studentNumber: cleanNum,
          password: loginPassword,
        });
      } catch (err: any) {
        console.error('Login error:', err);
        showAuthError(err.message, '学号或密码错误，请重新输入');
      } finally {
        setLoading(false);
      }
    } else {
      const phoneValidation = validateChineseMobile(loginPhone);
      if (!phoneValidation.isValid) {
        errors.loginPhone = phoneValidation.error!;
      }
      const cleanCode = loginSmsCode.trim();
      if (!cleanCode || cleanCode.length !== 6 || !/^\d{6}$/.test(cleanCode)) {
        errors.loginSmsCode = '请输入 6 位数字验证码';
      }

      if (Object.keys(errors).length > 0) {
        setFieldErrors(errors);
        return;
      }

      setLoading(true);
      try {
        await loginWithSms(phoneValidation.normalized || loginPhone.trim(), cleanCode);
      } catch (err: any) {
        console.error('SMS Login error:', err);
        showAuthError(err.message, '验证码错误或已失效，请重新输入');
      } finally {
        setLoading(false);
      }
    }
  };

  // 2. Handle Register Step 1 -> Send Code & Go to Step 2
  const handleStep1Next = async () => {
    setSnackbarOpen(false);
    const errors: Record<string, string> = {};

    const nameValidation = validatePersonName(regFullName);
    if (!nameValidation.isValid) {
      errors.regFullName = nameValidation.error!;
    }

    const numValidation = validateStudentNumber(regStudentNumber);
    if (!numValidation.isValid) {
      errors.regStudentNumber = numValidation.error!;
    }

    const phoneValidation = validateChineseMobile(regPhone);
    if (!phoneValidation.isValid) {
      errors.regPhone = phoneValidation.error!;
    }

    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      return;
    }

    setLoading(true);
    try {
      await intakeApi.sendCode(
        phoneValidation.normalized || regPhone.trim(),
        'REGISTRATION',
        numValidation.normalized || regStudentNumber.trim()
      );
      startCountdown();
      navigateTo('register_step2');
    } catch (err: any) {
      console.error('Send code error:', err);
      showAuthError(err.message, '发送验证码失败，请重试');
    } finally {
      setLoading(false);
    }
  };

  // Resend code handler in Step 2
  const handleResendCode = async () => {
    if (countdown > 0 || loading) return;
    setLoading(true);
    setSnackbarOpen(false);
    try {
      await intakeApi.sendCode(regPhone.trim(), 'REGISTRATION', regStudentNumber.trim());
      startCountdown();
    } catch (err: any) {
      console.error('Resend code error:', err);
      showAuthError(err.message, '重新获取验证码失败');
    } finally {
      setLoading(false);
    }
  };

  // 3. Handle Register Step 2 -> Verify Code & Go to Step 3
  const handleStep2Next = async () => {
    setSnackbarOpen(false);
    const cleanCode = verificationCode.trim();
    if (!cleanCode || cleanCode.length < 4) {
      setFieldErrors({ verificationCode: '请输入收到的验证码' });
      return;
    }

    setLoading(true);
    try {
      const res = await intakeApi.verifyCode(regPhone.trim(), cleanCode, 'REGISTRATION');
      if (!res.valid) {
        setFieldErrors({ verificationCode: '验证码不正确，请重新核对' });
        setLoading(false);
        return;
      }
      navigateTo('register_step3');
    } catch (err: any) {
      console.error('Verify code error:', err);
      showAuthError(err.message, '验证码校验失败');
    } finally {
      setLoading(false);
    }
  };

  // 4. Handle Register Step 3 -> Final Register Submit
  const handleStep3Submit = async () => {
    setSnackbarOpen(false);
    const errors: Record<string, string> = {};

    if (!regPassword || regPassword.length < 6) {
      errors.regPassword = '密码至少需要 6 位字符';
    } else if (regPassword.length > 64) {
      errors.regPassword = '密码长度不能超过 64 个字符';
    }
    if (regPassword !== regConfirmPassword) {
      errors.regConfirmPassword = '两次输入的密码不一致';
    }

    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      return;
    }

    setLoading(true);
    try {
      await register({
        studentNumber: validateStudentNumber(regStudentNumber).normalized || regStudentNumber.trim(),
        fullName: validatePersonName(regFullName).normalized || regFullName.trim(),
        phone: validateChineseMobile(regPhone).normalized || regPhone.trim(),
        password: regPassword,
        verificationCode: verificationCode.trim(),
      });
    } catch (err: any) {
      console.error('Register error:', err);
      showAuthError(err.message, '登记建档失败，请重试');
    } finally {
      setLoading(false);
    }
  };

  // Shared Brand Logo Header matching Google top-left logo placement, using theme color
  const renderLogo = () => (
    <div className="mb-6 select-none">
      <span className="text-[34px] font-extrabold text-[var(--md-sys-color-primary)] tracking-tight font-sans">
        CSU
      </span>
    </div>
  );

  return (
    <div className="w-full min-h-[100dvh] bg-[var(--md-sys-color-surface)] text-[var(--md-sys-color-on-surface)] flex flex-col items-center px-6 py-8 sm:py-12">
      {/* Top indeterminate progress bar during network requests */}
      <div className="h-1 w-full fixed top-0 left-0 z-50">
        {loading && (
          <md-linear-progress
            indeterminate
            style={{ width: '100%', '--md-linear-progress-track-height': '3px' } as any}
          />
        )}
      </div>

      <div className="w-full max-w-[440px] flex-1 flex flex-col">
        {/* Main Body */}
        {renderLogo()}

        <div className="w-full relative overflow-hidden flex-1 flex flex-col">
          <AnimatePresence mode="wait" custom={{ direction, isStepSlide }} initial={false}>
            {/* ==================== VIEW 1: 登录 (Sign in) ==================== */}
            {view === 'login' && (
              <motion.div
                key="login"
                custom={{ direction, isStepSlide }}
                variants={pageVariants}
                initial="enter"
                animate="center"
                exit="exit"
                transition={getTransition(isStepSlide)}
                className="w-full flex flex-col"
              >
                <h1 className="text-[32px] sm:text-[36px] font-normal leading-[40px] sm:leading-[44px] text-[var(--md-sys-color-on-surface)] tracking-tight">
                  登录
                </h1>
                <p className="text-[15px] sm:text-[16px] leading-[24px] text-[var(--md-sys-color-on-surface-variant)] mt-3 mb-6 font-normal">
                  {loginMode === 'password'
                    ? '使用您的中南大学学号以继续心理普查'
                    : '输入已登记手机号码及短信验证码快捷登录'}
                </p>

                {/* Segmented Button: Tab Switcher between Password and SMS Login */}
                <div className="mb-6 flex">
                  <SegmentedButton
                    items={LOGIN_MODE_ITEMS}
                    selectedValue={loginMode}
                    onChange={(val) => {
                      if (loading) return;
                      setLoginMode(val as LoginMode);
                      setFieldErrors({});
                    }}
                  />
                </div>

                <form onSubmit={handleLoginSubmit} className="space-y-6">
                  {loginMode === 'password' ? (
                    <>
                      <div>
                        <md-outlined-text-field
                          label="学号 / 工号"
                          value={loginStudentNumber}
                          required
                          disabled={loading}
                          className="w-full"
                          error={!!fieldErrors.loginStudentNumber}
                          error-text={fieldErrors.loginStudentNumber}
                          onInput={(e: any) => {
                            setLoginStudentNumber(e.target.value);
                            clearFieldError('loginStudentNumber');
                          }}
                        />
                      </div>

                      <div>
                        <md-outlined-text-field
                          label="登录密码"
                          type={showLoginPassword ? 'text' : 'password'}
                          value={loginPassword}
                          required
                          disabled={loading}
                          className="w-full"
                          error={!!fieldErrors.loginPassword}
                          error-text={fieldErrors.loginPassword}
                          onInput={(e: any) => {
                            setLoginPassword(e.target.value);
                            clearFieldError('loginPassword');
                          }}
                        >
                          <md-icon-button
                            type="button"
                            slot="trailing-icon"
                            disabled={loading}
                            onClick={() => setShowLoginPassword(!showLoginPassword)}
                            aria-label={showLoginPassword ? '隐藏密码' : '显示密码'}
                          >
                            <md-icon>{showLoginPassword ? 'visibility_off' : 'visibility'}</md-icon>
                          </md-icon-button>
                        </md-outlined-text-field>
                      </div>

                      <div className="pt-1">
                        <button
                          type="button"
                          disabled={loading}
                          onClick={() => alert('请联系校区辅导员或心理健康中心管理员协助找回学号与密码。')}
                          className="text-[14px] font-medium text-[var(--md-sys-color-primary)] hover:underline disabled:opacity-40 disabled:pointer-events-none"
                        >
                          忘记了学号或密码？
                        </button>
                      </div>
                    </>
                  ) : (
                    <>
                      <div>
                        <md-outlined-text-field
                          type="tel"
                          label="手机号码"
                          value={loginPhone}
                          required
                          disabled={loading}
                          className="w-full"
                          error={!!fieldErrors.loginPhone}
                          error-text={fieldErrors.loginPhone}
                          supporting-text="请输入已注册的 11 位手机号码"
                          onInput={(e: any) => {
                            setLoginPhone(e.target.value);
                            clearFieldError('loginPhone');
                          }}
                        />
                      </div>

                      <div>
                        <md-outlined-text-field
                          type="tel"
                          inputmode="numeric"
                          label="6 位验证码"
                          value={loginSmsCode}
                          maxLength={6}
                          required
                          disabled={loading}
                          className="w-full"
                          style={{
                            '--md-outlined-text-field-with-trailing-icon-trailing-space': '6px',
                            '--md-outlined-field-with-trailing-content-trailing-space': '6px',
                          } as any}
                          error={!!fieldErrors.loginSmsCode}
                          error-text={fieldErrors.loginSmsCode}
                          onInput={(e: any) => {
                            setLoginSmsCode(e.target.value);
                            clearFieldError('loginSmsCode');
                          }}
                        >
                          <button
                            type="button"
                            slot="trailing-icon"
                            disabled={loginCountdown > 0 || loading}
                            onClick={(e) => {
                              e.stopPropagation();
                              handleSendLoginCode();
                            }}
                            className="h-10 min-h-[40px] px-2.5 text-xs font-semibold text-[var(--md-sys-color-primary)] hover:bg-[var(--md-sys-color-primary)]/10 rounded-full transition-colors whitespace-nowrap disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer flex items-center justify-center shrink-0"
                            style={{ right: 0, position: 'absolute' }}
                          >
                            {loginCountdown > 0 ? `${loginCountdown}s` : '获取验证码'}
                          </button>
                        </md-outlined-text-field>
                      </div>
                    </>
                  )}

                  {/* Action Row: Left: Create account, Right: Next */}
                  <div className="flex items-center justify-between mt-10 pt-4">
                    <button
                      type="button"
                      disabled={loading}
                      onClick={() => {
                        setSnackbarOpen(false);
                        setFieldErrors({});
                        navigateTo('register_step1');
                      }}
                      className="text-[var(--md-sys-color-primary)] text-[14px] font-medium hover:bg-[var(--md-sys-color-primary)]/10 px-4 py-2.5 rounded-full transition-colors disabled:opacity-40 disabled:pointer-events-none"
                    >
                      创建账号
                    </button>

                    <PrimaryButton
                      label="下一步"
                      disabled={loading}
                      onClick={() => handleLoginSubmit()}
                      className="h-10 min-h-[40px] px-6 text-[14px] font-medium rounded-full"
                    />
                  </div>
                </form>
              </motion.div>
            )}

            {/* ==================== VIEW 2: 注册第 1 步 (Create Account - Names & Phone) ==================== */}
            {view === 'register_step1' && (
              <motion.div
                key="register_step1"
                custom={{ direction, isStepSlide }}
                variants={pageVariants}
                initial="enter"
                animate="center"
                exit="exit"
                transition={getTransition(isStepSlide)}
                className="w-full flex flex-col"
              >
                <h1 className="text-[32px] sm:text-[36px] font-normal leading-[40px] sm:leading-[44px] text-[var(--md-sys-color-on-surface)] tracking-tight">
                  创建账号
                </h1>
                <p className="text-[15px] sm:text-[16px] leading-[24px] text-[var(--md-sys-color-on-surface-variant)] mt-3 mb-8 font-normal">
                  输入您的基本信息以建立新生档案
                </p>

                <div className="space-y-6">
                  <div>
                    <md-outlined-text-field
                      label="真实姓名"
                      value={regFullName}
                      required
                      disabled={loading}
                      className="w-full"
                      error={!!fieldErrors.regFullName}
                      error-text={fieldErrors.regFullName}
                      onInput={(e: any) => {
                        setRegFullName(e.target.value);
                        clearFieldError('regFullName');
                      }}
                    />
                  </div>

                  <div>
                    <md-outlined-text-field
                      label="学号"
                      value={regStudentNumber}
                      required
                      disabled={loading}
                      className="w-full"
                      error={!!fieldErrors.regStudentNumber}
                      error-text={fieldErrors.regStudentNumber}
                      supporting-text="校方分配的个人学号"
                      onInput={(e: any) => {
                        setRegStudentNumber(e.target.value);
                        clearFieldError('regStudentNumber');
                      }}
                    />
                  </div>

                  <div>
                    <md-outlined-text-field
                      type="tel"
                      label="手机号码"
                      value={regPhone}
                      required
                      disabled={loading}
                      className="w-full"
                      error={!!fieldErrors.regPhone}
                      error-text={fieldErrors.regPhone}
                      supporting-text="用于接收短信验证码"
                      onInput={(e: any) => {
                        setRegPhone(e.target.value);
                        clearFieldError('regPhone');
                      }}
                    />
                  </div>

                  {/* Action Row */}
                  <div className="flex items-center justify-between mt-10 pt-4">
                    <button
                      type="button"
                      disabled={loading}
                      onClick={() => {
                        setSnackbarOpen(false);
                        setFieldErrors({});
                        navigateTo('login');
                      }}
                      className="text-[var(--md-sys-color-primary)] text-[14px] font-medium hover:bg-[var(--md-sys-color-primary)]/10 px-4 py-2.5 rounded-full transition-colors disabled:opacity-40 disabled:pointer-events-none"
                    >
                      改为登录
                    </button>

                    <PrimaryButton
                      label="下一步"
                      disabled={loading}
                      onClick={handleStep1Next}
                      className="h-10 min-h-[40px] px-6 text-[14px] font-medium rounded-full"
                    />
                  </div>
                </div>
              </motion.div>
            )}

            {/* ==================== VIEW 3: 注册第 2 步 (Verify Phone) ==================== */}
            {view === 'register_step2' && (
              <motion.div
                key="register_step2"
                custom={{ direction, isStepSlide }}
                variants={pageVariants}
                initial="enter"
                animate="center"
                exit="exit"
                transition={getTransition(isStepSlide)}
                className="w-full flex flex-col"
              >
                <h1 className="text-[32px] sm:text-[36px] font-normal leading-[40px] sm:leading-[44px] text-[var(--md-sys-color-on-surface)] tracking-tight">
                  验证手机号码
                </h1>

                <div className="mt-3 mb-8 text-[15px] sm:text-[16px] leading-[24px] text-[var(--md-sys-color-on-surface-variant)] font-normal">
                  <span>系统已向以下号码发送了一条包含 6 位验证码的短信：</span>
                  <div className="mt-2 flex items-center gap-2">
                    <span className="font-mono font-semibold text-[var(--md-sys-color-on-surface)] text-[16px]">{regPhone}</span>
                    <button
                      type="button"
                      disabled={loading}
                      onClick={() => navigateTo('register_step1')}
                      className="text-[13px] text-[var(--md-sys-color-primary)] hover:underline font-medium disabled:opacity-40 disabled:pointer-events-none"
                    >
                      修改号码
                    </button>
                  </div>
                </div>

                <div className="space-y-6">
                  <div>
                    <md-outlined-text-field
                      type="tel"
                      inputmode="numeric"
                      label="6 位短信验证码"
                      value={verificationCode}
                      maxLength={6}
                      required
                      disabled={loading}
                      className="w-full"
                      style={{
                        '--md-outlined-text-field-with-trailing-icon-trailing-space': '6px',
                        '--md-outlined-field-with-trailing-content-trailing-space': '6px',
                      } as any}
                      error={!!fieldErrors.verificationCode}
                      error-text={fieldErrors.verificationCode}
                      supporting-text="请输入短信中收到的 6 位验证码"
                      onInput={(e: any) => {
                        setVerificationCode(e.target.value);
                        clearFieldError('verificationCode');
                      }}
                    >
                      <button
                        type="button"
                        slot="trailing-icon"
                        disabled={countdown > 0 || loading}
                        onClick={(e) => {
                          e.stopPropagation();
                          handleResendCode();
                        }}
                        className="h-10 min-h-[40px] px-2.5 text-xs font-semibold text-[var(--md-sys-color-primary)] hover:bg-[var(--md-sys-color-primary)]/10 rounded-full transition-colors whitespace-nowrap disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer flex items-center justify-center shrink-0"
                        style={{ right: 0, position: 'absolute' }}
                      >
                        {countdown > 0 ? `${countdown}s` : '重新获取'}
                      </button>
                    </md-outlined-text-field>
                  </div>

                  {/* Action Row */}
                  <div className="flex items-center justify-between mt-10 pt-4">
                    <button
                      type="button"
                      disabled={loading}
                      onClick={() => {
                        setSnackbarOpen(false);
                        setFieldErrors({});
                        navigateTo('register_step1');
                      }}
                      className="text-[var(--md-sys-color-primary)] text-[14px] font-medium hover:bg-[var(--md-sys-color-primary)]/10 px-4 py-2.5 rounded-full transition-colors disabled:opacity-40 disabled:pointer-events-none"
                    >
                      上一步
                    </button>

                    <PrimaryButton
                      label="下一步"
                      disabled={loading}
                      onClick={handleStep2Next}
                      className="h-10 min-h-[40px] px-6 text-[14px] font-medium rounded-full"
                    />
                  </div>
                </div>
              </motion.div>
            )}

            {/* ==================== VIEW 4: 注册第 3 步 (Set Password) ==================== */}
            {view === 'register_step3' && (
              <motion.div
                key="register_step3"
                custom={{ direction, isStepSlide }}
                variants={pageVariants}
                initial="enter"
                animate="center"
                exit="exit"
                transition={getTransition(isStepSlide)}
                className="w-full flex flex-col"
              >
                <h1 className="text-[32px] sm:text-[36px] font-normal leading-[40px] sm:leading-[44px] text-[var(--md-sys-color-on-surface)] tracking-tight">
                  创建安全密码
                </h1>
                <p className="text-[15px] sm:text-[16px] leading-[24px] text-[var(--md-sys-color-on-surface-variant)] mt-3 mb-8 font-normal">
                  设置至少 6 位字符的密码以保护您的心理档案安全
                </p>

                <div className="space-y-6">
                  <div>
                    <md-outlined-text-field
                      label="登录密码"
                      type={showRegPassword ? 'text' : 'password'}
                      value={regPassword}
                      required
                      disabled={loading}
                      className="w-full"
                      error={!!fieldErrors.regPassword}
                      error-text={fieldErrors.regPassword}
                      supporting-text="至少 6 位字符"
                      onInput={(e: any) => {
                        setRegPassword(e.target.value);
                        clearFieldError('regPassword');
                      }}
                    >
                      <md-icon-button
                        type="button"
                        slot="trailing-icon"
                        disabled={loading}
                        onClick={() => setShowRegPassword(!showRegPassword)}
                        aria-label={showRegPassword ? '隐藏密码' : '显示密码'}
                      >
                        <md-icon>{showRegPassword ? 'visibility_off' : 'visibility'}</md-icon>
                      </md-icon-button>
                    </md-outlined-text-field>
                  </div>

                  <div>
                    <md-outlined-text-field
                      label="确认密码"
                      type={showRegConfirmPassword ? 'text' : 'password'}
                      value={regConfirmPassword}
                      required
                      disabled={loading}
                      className="w-full"
                      error={!!fieldErrors.regConfirmPassword}
                      error-text={fieldErrors.regConfirmPassword}
                      supporting-text="请再次输入新密码"
                      onInput={(e: any) => {
                        setRegConfirmPassword(e.target.value);
                        clearFieldError('regConfirmPassword');
                      }}
                    >
                      <md-icon-button
                        type="button"
                        slot="trailing-icon"
                        disabled={loading}
                        onClick={() => setShowRegConfirmPassword(!showRegConfirmPassword)}
                        aria-label={showRegConfirmPassword ? '隐藏密码' : '显示密码'}
                      >
                        <md-icon>{showRegConfirmPassword ? 'visibility_off' : 'visibility'}</md-icon>
                      </md-icon-button>
                    </md-outlined-text-field>
                  </div>

                  {/* Action Row */}
                  <div className="flex items-center justify-between mt-10 pt-4">
                    <button
                      type="button"
                      disabled={loading}
                      onClick={() => {
                        setSnackbarOpen(false);
                        setFieldErrors({});
                        navigateTo('register_step2');
                      }}
                      className="text-[var(--md-sys-color-primary)] text-[14px] font-medium hover:bg-[var(--md-sys-color-primary)]/10 px-4 py-2.5 rounded-full transition-colors disabled:opacity-40 disabled:pointer-events-none"
                    >
                      上一步
                    </button>

                    <PrimaryButton
                      label="完成"
                      disabled={loading}
                      onClick={handleStep3Submit}
                      className="h-10 min-h-[40px] px-6 text-[14px] font-medium rounded-full"
                    />
                  </div>
                </div>
              </motion.div>
            )}
          </AnimatePresence>
        </div>

        {/* Google-style bottom theme switcher and university footer on the same line */}
        <div className="w-full mt-auto pt-10 pb-4 flex items-center justify-between text-xs text-[var(--md-sys-color-on-surface-variant)]">
          <div className="relative inline-flex items-center">
            <button
              ref={themeMenuAnchorRef}
              id="theme-menu-anchor"
              type="button"
              onClick={toggleThemeMenu}
              aria-label="选择界面外观主题"
              aria-haspopup="menu"
              aria-expanded={isThemeMenuOpen}
              className="inline-flex items-center gap-0.5 bg-transparent py-1 text-xs font-normal text-[var(--md-sys-color-on-surface-variant)] hover:text-[var(--md-sys-color-on-surface)] cursor-pointer focus:outline-none focus:underline rounded-md select-none"
            >
              <span>{theme === 'dark' ? '深色模式' : '浅色模式'}</span>
              <md-icon class="text-[18px] text-[var(--md-sys-color-on-surface-variant)] pointer-events-none">
                arrow_drop_down
              </md-icon>
            </button>

            <md-menu
              ref={themeMenuRef}
              id="theme-menu"
              anchor="theme-menu-anchor"
              open={isThemeMenuOpen}
              anchor-corner="start-start"
              menu-corner="end-start"
              onclosed={() => setIsThemeMenuOpen(false)}
              onclosing={() => setIsThemeMenuOpen(false)}
              onClosed={() => setIsThemeMenuOpen(false)}
              style={{
                minWidth: '200px',
                '--md-menu-container-shape': '12px',
              } as React.CSSProperties}
            >
              <md-menu-item
                selected={theme === 'light'}
                onClick={() => {
                  setTheme('light');
                  setIsThemeMenuOpen(false);
                }}
              >
                <md-icon slot="start">light_mode</md-icon>
                <div slot="headline" className="whitespace-nowrap">浅色模式</div>
                {theme === 'light' && <md-icon slot="end">check</md-icon>}
              </md-menu-item>
              <md-menu-item
                selected={theme === 'dark'}
                onClick={() => {
                  setTheme('dark');
                  setIsThemeMenuOpen(false);
                }}
              >
                <md-icon slot="start">dark_mode</md-icon>
                <div slot="headline" className="whitespace-nowrap">深色模式</div>
                {theme === 'dark' && <md-icon slot="end">check</md-icon>}
              </md-menu-item>
            </md-menu>
          </div>
          <div className="flex items-center gap-4 text-[11px] text-[var(--md-sys-color-outline)]">
            <span>中南大学</span>
            <span>心理普查</span>
          </div>
        </div>
      </div>

      {/* MD3 Bottom Error / Notification Snackbar */}
      <Snackbar
        open={snackbarOpen}
        message={snackbarMessage}
        variant={snackbarVariant}
        icon="error"
        actionLabel={snackbarActionLabel}
        onAction={snackbarAction}
        onClose={() => setSnackbarOpen(false)}
        duration={snackbarAction ? 7000 : 4000}
      />
    </div>
  );
};
