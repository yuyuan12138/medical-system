import { apiFetch } from './client';

export interface HighRiskStudentDto {
  studentId: number;
  studentName: string;
  studentNumber: string;
  riskStatus: 'HIGH' | 'MEDIUM' | 'LOW';
  riskFlags: string[];
  scidDiagnosis: string | null;
}

export async function fetchHighRiskStudents(token?: string, includeMedium = false): Promise<HighRiskStudentDto[]> {
  return apiFetch<HighRiskStudentDto[]>(`/api/students/high-risk?includeMedium=${includeMedium}`, { token });
}
