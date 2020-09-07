export function getUserTimezone() {
  let timezoneHours = eXo.env.portal.timezoneOffset / 3600000;
  let timezoneMinutes = eXo.env.portal.timezoneOffset % 3600000 / 60000;
  timezoneHours = timezoneHours < 10 ? `0${timezoneHours}` : timezoneHours;
  timezoneMinutes = timezoneMinutes < 10 ? `0${timezoneMinutes}` : timezoneMinutes;
  const timezoneSign = eXo.env.portal.timezoneOffset >= 0 ? '+' : '-';
  return `${timezoneSign}${timezoneHours}:${timezoneMinutes}`;
}

export function convertVuetifyRangeToPeriod(range, userTimezone) {
  const rangeStartHour = range.start.hour < 10 ? `0${range.start.hour}` : range.start.hour;
  const rangeStartMinute = range.start.minute < 10 ? `0${range.start.minute}` : range.start.minute;
  const rangeEndHour = range.end.hour < 10 ? `0${range.end.hour}` : range.end.hour;
  const rangeEndMinute = range.end.minute < 10 ? `0${range.end.minute}` : range.end.minute;
  return {
    start: `${range.start.date}T${rangeStartHour}:${rangeStartMinute}:00.000${userTimezone}`,
    end: `${range.end.date}T${rangeEndHour}:${rangeEndMinute}:00.000${userTimezone}`,
  };
}

export function getWeekNumber(date) {
  const dayNumber = (date.getDay() + 6) % 7;
  date.setDate(date.getDate() - dayNumber + 3);
  const firstThursday = date.valueOf();
  date.setMonth(0, 1);
  if (date.getDay() !== 4) {
    date.setMonth(0, 1 + (4 - date.getDay() + 7) % 7);
  }
  return Math.ceil((firstThursday - date.valueOf()) / 604800000);
}

export function toVuetifyDate(date) {
  return `${date.getFullYear()}-${ 
    pad(date.getMonth() + 1)}-${ 
    pad(date.getDate())} ${
    pad(date.getHours())}:${ 
    pad(date.getMinutes())}`;
}

export function toRFC3339(date) {
  if (typeof date === 'number') {
    date = new Date(date);
  }
  return `${date.getFullYear()  }-${ 
    pad(date.getMonth() + 1)  }-${ 
    pad(date.getDate())  }T${ 
    pad(date.getHours())  }:${ 
    pad(date.getMinutes())  }:${ 
    pad(date.getSeconds())  
  }${getUserTimezone()}`;
}

export function generateCalendarTitle(calendarType, startDate, periodTitle, weekTitle) {
  if(calendarType === 'week') {
    const weekNumber = getWeekNumber(startDate);
    return `${periodTitle} - ${weekTitle} ${weekNumber}`;
  } else if (calendarType === 'day') {
    const currentDay = startDate.getDate();
    return `${periodTitle} - ${currentDay}`;
  } else if (calendarType === 'month') {
    return periodTitle;
  }
}

function pad(n) {
  return n < 10 && `0${n}` || n;
}
