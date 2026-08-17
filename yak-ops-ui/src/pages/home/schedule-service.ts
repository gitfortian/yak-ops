import type { ApiResponse } from '@/services/http/response';
import HttpUtils from '@/utils/HttpUtils';
import type { HomeTaskType } from './service';

export interface HomeScheduleOccurrence {
  taskId: string;
  taskType: HomeTaskType;
  taskName: string;
  time: string;
  scheduleText: string;
  detailPath: string;
}

export interface HomeScheduleDay {
  date: string;
  count: number;
  items: HomeScheduleOccurrence[];
}

export interface HomeScheduleSummary {
  taskId: string;
  taskType: HomeTaskType;
  taskName: string;
  scheduleText: string;
  nextRunDate: string;
  nextRunTime: string;
  detailPath: string;
}

export interface HomeScheduleCalendar {
  month: string;
  totalSchedules: number;
  days: HomeScheduleDay[];
  overview: HomeScheduleSummary[];
}

const PREFIX = '/api/v1/home/schedule-center';

export const homeScheduleCenterApi = {
  calendar: (month: string): Promise<ApiResponse<HomeScheduleCalendar>> =>
    HttpUtils.get<HomeScheduleCalendar>(
      `${PREFIX}/calendar?month=${encodeURIComponent(month)}`,
    ),
};
