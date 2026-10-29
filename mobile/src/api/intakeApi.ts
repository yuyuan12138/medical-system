/**
 * Mobile Intake API Client
 * Interacts with Spring Boot Intake Backend (/api)
 */

export interface StudentDto {
  studentNumber: string;
  fullName: string;
  phone: string;
  registeredAt: string;
}

export interface AuthResponse {
  token: string;
  studentNumber?: string;
  fullName?: string;
  phone?: string;
  student?: StudentDto;
  role?: 'STUDENT' | 'TEACHER' | 'DOCTOR' | 'HEAD_COUNSELLOR' | 'ADMIN' | string;
  name?: string;
  userId?: number;
}

export interface ReferralDto {
  id: string;
  studentId: number;
  studentName: string;
  studentNumber: string;
  type: string;
  date: string;
  title: string;
  description: string;
  riskLevel: 'HIGH' | 'MEDIUM' | 'LOW';
  status: string;
  referredBy: { name: string };
}

export interface ReferralDetailsDto {
  baseInfo: ReferralDto;
  triageInfo: {
    isFirstVisit: boolean;
    isMedicated: boolean;
    priorTherapy: string;
    scidDiagnosis?: string | null;
    fullDescription: string;
  };
  riskAssessment: {
    ideation: boolean;
    attempt: boolean;
    selfHarm: boolean;
  };
  feedback?: { content?: string } | null;
}

export interface ReferralTrackingDto {
  destination?: {
    hospital: string;
    department: string;
    doctor: string;
    transferDate?: string | null;
    appointmentTime?: string | null;
  } | null;
  steps?: {
    type: string;
    completedAt?: string | null;
    operator?: string | null;
    outcome?: string | null;
  }[] | null;
}

export interface AssessmentCatalogItem {
  batteryCode: string;
  title: string;
  subtitle?: string | null;
  duration: string;
  questionCount: number;
  isEnabled: boolean;
}

export interface StaffStudentDto {
  id: string;
  studentNumber?: string;
  name: string;
  major: string;
  riskLevel?: 'HIGH' | 'MEDIUM' | 'LOW';
}

export interface RegisterRequest {
  studentNumber: string;
  fullName: string;
  phone: string;
  password?: string;
  verificationCode?: string;
}

export interface CheckAvailabilityResponse {
  studentNumberAvailable: boolean;
  phoneAvailable: boolean;
  message?: string;
}

export interface LoginRequest {
  identifier?: string;
  studentNumber?: string;
  password?: string;
}

export interface ScaleSummaryDto {
  code: string;
  title: string;
  subtitle?: string;
  description: string;
  questionCount: number;
  estimatedMinutes: number;
  status: 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED';
  answeredCount?: number;
  completionPercentage?: number;
}

export interface ScaleDraftDto {
  scaleCode: string;
  answers: Record<string, any>;
  updatedAt: number;
}

export interface ScaleOption {
  value: any;
  label: string;
  hasTextInput?: boolean;
  textInputPlaceholder?: string;
  textInputLabel?: string;
}

export interface ScaleQuestion {
  id: string;
  text: string;
  orderNum: number;
  type: string; // 'single_choice' | 'multiple_choice' | 'number' | 'slider' | 'text' | 'select' | 'date'
  field?: string;
  placeholder?: string;
  min?: number;
  max?: number;
  step?: number;
  unit?: string;
  minLabel?: string;
  maxLabel?: string;
  options: ScaleOption[];
  sectionCode?: string;
  sectionTitle?: string;
  icon?: string;
  zeroOptionLabel?: string;
}

export interface BatterySection {
  code: string;
  title: string;
  questionCount: number;
  orderNum: number;
}

export interface ScaleIntroItem {
  icon?: string;
  title: string;
  description: string;
}

export interface ScaleDetail {
  code: string;
  title: string;
  subtitle?: string;
  description: string;
  estimatedMinutes: number;
  instructions?: string;
  introItems?: ScaleIntroItem[];
  questions: ScaleQuestion[];
  sections?: BatterySection[];
}

export interface StudentDemographicsDto {
  isSubmitted: boolean;
  gender?: string | null;
  ethnicity?: string | null;
  major?: string | null;
  birthday?: string | null;
  idCardNumber?: string | null;
  email?: string | null;
  homeAddress?: string | null;
  emergencyContact?: string | null;
  emergencyPhone?: string | null;
  completedAt?: string | null;
}

export interface StudentProfileDto {
  studentNumber: string;
  fullName: string;
  phone: string;
  registeredAt: string;
  isDemographicsSubmitted: boolean;
  demographics: StudentDemographicsDto;
}

export interface SubmitScaleResponse {
  scaleCode: string;
  status: string;
  completedAt: string;
}

const TOKEN_KEY = 'mobile_intake_token';

export const tokenStorage = {
  get: (): string | null => localStorage.getItem(TOKEN_KEY),
  set: (token: string) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY),
};

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = tokenStorage.get();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string> || {}),
  };

  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const res = await fetch(path, {
    ...options,
    headers,
    keepalive: options.keepalive,
  });

  if (!res.ok) {
    let errorMsg = '网络请求失败';
    try {
      const errData = await res.json();
      errorMsg = errData.error || errData.message || `请求失败 (${res.status})`;
    } catch {
      errorMsg = `请求失败 (${res.status})`;
    }
    throw new Error(errorMsg);
  }

  if (res.status === 204) {
    return null as unknown as T;
  }

  const text = await res.text();
  if (!text) {
    return null as unknown as T;
  }

  try {
    return JSON.parse(text) as T;
  } catch {
    return text as unknown as T;
  }
}

export const intakeApi = {
  // Auth
  register: (data: RegisterRequest) =>
    request<AuthResponse>('/api/auth/student-register', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  login: (data: LoginRequest) => {
    const id = data.identifier || data.studentNumber || '';
    return request<AuthResponse>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({
        identifier: id,
        studentNumber: id,
        password: data.password || '',
      }),
    });
  },

  loginWithSms: (phone: string, code: string) =>
    request<AuthResponse>('/api/auth/login-sms', {
      method: 'POST',
      body: JSON.stringify({ phone, code }),
    }),

  sendCode: (phone: string, purpose: 'REGISTRATION' | 'LOGIN' = 'REGISTRATION', studentNumber?: string) =>
    request<{ phone: string; devCode?: string; expiresInSeconds: number }>('/api/auth/send-code', {
      method: 'POST',
      body: JSON.stringify({ phone, purpose, studentNumber: studentNumber || undefined }),
    }),

  checkAvailability: (studentNumber?: string, phone?: string) =>
    request<CheckAvailabilityResponse>(
      `/api/auth/check-availability?${new URLSearchParams({
        ...(studentNumber ? { studentNumber } : {}),
        ...(phone ? { phone } : {}),
      }).toString()}`
    ),

  verifyCode: (phone: string, code: string, purpose: 'REGISTRATION' | 'LOGIN' = 'REGISTRATION') =>
    request<{ valid: boolean }>('/api/auth/verify-code', {
      method: 'POST',
      body: JSON.stringify({ phone, code, purpose }),
    }),

  getMe: () => request<StudentDto>('/api/auth/me'),

  getMyProfile: () => request<StudentProfileDto>('/api/students/me/profile'),

  // Scales
  getScales: () => request<ScaleSummaryDto[]>('/api/scales'),

  getScaleDetail: (code: string) => request<ScaleDetail>(`/api/scales/${code}`),

  submitScale: (code: string, answers: Record<string, any>) =>
    request<SubmitScaleResponse>(`/api/scales/${code}/submit`, {
      method: 'POST',
      body: JSON.stringify({ answers }),
    }),

  getSubmission: (code: string) =>
    request<{ scaleCode: string; status: string; completedAt: string; answers: Record<string, any> }>(
      `/api/scales/${code}/submission`
    ),

  // In-session drafts
  getDraft: (code: string) =>
    request<ScaleDraftDto | null>(`/api/scales/${code}/draft`),

  saveDraft: (code: string, data: { answers: Record<string, any>; updatedAt: number }, keepalive?: boolean) =>
    request<void>(`/api/scales/${code}/draft`, {
      method: 'PUT',
      body: JSON.stringify(data),
      keepalive,
    }),

  deleteDraft: (code: string) =>
    request<void>(`/api/scales/${code}/draft`, {
      method: 'DELETE',
    }),

  // Referrals (unified backend)
  getReferrals: () => request<ReferralDto[]>('/api/referrals'),

  getReferralDetails: (id: string) => request<ReferralDetailsDto>(`/api/referrals/${id}`),

  getReferralTracking: (id: string) => request<ReferralTrackingDto>(`/api/referrals/${id}/tracking`),

  // Teacher assessment dispatch
  getAssessmentCatalog: () => request<AssessmentCatalogItem[]>('/api/assessments/catalog'),

  getStaffStudents: () => request<StaffStudentDto[]>('/api/students'),

  assignAssessment: (studentId: number, batteryCode: string, dueDate?: string) =>
    request<{ assignedCount: number }>('/api/assessments/assign', {
      method: 'POST',
      body: JSON.stringify({
        studentId,
        batteryCodes: [batteryCode],
        dueDate: dueDate || undefined,
      }),
    }),
};
