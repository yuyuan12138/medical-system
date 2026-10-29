import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { intakeApi, tokenStorage, StudentDto, LoginRequest, RegisterRequest } from '../api/intakeApi';

export interface AuthContextType {
  student: StudentDto | null;
  token: string | null;
  role: string | null;
  loading: boolean;
  login: (req: LoginRequest) => Promise<void>;
  loginWithSms: (phone: string, code: string) => Promise<void>;
  register: (req: RegisterRequest) => Promise<void>;
  logout: () => void;
  refreshStudent: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [student, setStudent] = useState<StudentDto | null>(null);
  const [token, setToken] = useState<string | null>(tokenStorage.get());
  const [role, setRole] = useState<string | null>(localStorage.getItem('mh_role'));
  const [loading, setLoading] = useState<boolean>(true);

  const refreshStudent = useCallback(async () => {
    const currentToken = tokenStorage.get();
    if (!currentToken) {
      setStudent(null);
      setLoading(false);
      return;
    }
    try {
      const me = await intakeApi.getMe();
      setStudent(me);
    } catch (err) {
      console.warn('Failed to validate session token, clearing credentials:', err);
      tokenStorage.clear();
      setToken(null);
      setStudent(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refreshStudent();
  }, [refreshStudent]);

  const applyAuthResponse = (res: import('../api/intakeApi').AuthResponse, fallbackName?: string, fallbackPhone?: string) => {
    tokenStorage.set(res.token);
    setToken(res.token);
    if (res.role) {
      localStorage.setItem('mh_role', res.role);
      setRole(res.role);
    }
    const s = res.student;
    setStudent({
      studentNumber: s?.studentNumber || res.studentNumber || '',
      fullName: s?.fullName || res.fullName || res.name || fallbackName || '',
      phone: s?.phone || res.phone || fallbackPhone || '',
      registeredAt: (s as any)?.createdAt || (s as any)?.registeredAt || new Date().toISOString(),
    });
  };

  const login = async (req: LoginRequest) => {
    const res = await intakeApi.login(req);
    applyAuthResponse(res, req.identifier || req.studentNumber);
  };

  const loginWithSms = async (phone: string, code: string) => {
    const res = await intakeApi.loginWithSms(phone, code);
    applyAuthResponse(res, undefined, phone);
  };

  const register = async (req: RegisterRequest) => {
    const res = await intakeApi.register(req);
    applyAuthResponse(res, req.fullName, req.phone);
  };

  const logout = () => {
    tokenStorage.clear();
    localStorage.removeItem('mh_role');
    setToken(null);
    setStudent(null);
    setRole(null);
  };

  return (
    <AuthContext.Provider
      value={{
        student,
        token,
        role,
        loading,
        login,
        loginWithSms,
        register,
        logout,
        refreshStudent,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export function useAuth(): AuthContextType {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}

export function useOptionalAuth(): AuthContextType | null {
  return useContext(AuthContext) ?? null;
}
