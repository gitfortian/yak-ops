export type CronSchedulePeriod = "minute" | "hour" | "day" | "week" | "month" | "year";
export type CronHourMode = "range" | "specified";

/** Quartz weekday: Sunday=1, Monday=2 ... Saturday=7. */
export type CronQuartzWeekday = 1 | 2 | 3 | 4 | 5 | 6 | 7;

export type CronMonthRule =
  | { kind: "day"; day: number }
  | { kind: "nthWeekday"; nth: 1 | 2 | 3 | 4; weekday: CronQuartzWeekday }
  | { kind: "lastDay" }
  | { kind: "lastWeekday"; weekday: CronQuartzWeekday };

export interface CronScheduleConfig {
  period: CronSchedulePeriod;

  minuteStartTime: string;
  minuteInterval: number;
  minuteEndTime: string;

  hourMode: CronHourMode;
  hourStartTime: string;
  hourInterval: number;
  hourEndTime: string;
  specifiedHours: number[];
  specifiedHourMinute: number;

  dayTime: string;

  weekdays: CronQuartzWeekday[];
  weekTime: string;

  monthRules: CronMonthRule[];
  monthTime: string;

  months: number[];
  yearRules: CronMonthRule[];
  yearTime: string;
}

export interface CronGenerationResult {
  cron: string;
  fields: {
    second: string;
    minute: string;
    hour: string;
    dayOfMonth: string;
    month: string;
    dayOfWeek: string;
  };
}
