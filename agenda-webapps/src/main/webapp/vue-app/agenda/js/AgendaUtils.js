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

export function getDayNameFromDate(date, lang) {
  const options = { weekday: 'long' };
  let d = null;
  if (date) {
    d = new Date(date);
  } else {
    d = new Date();
  }
  d = d.toLocaleDateString(lang || 'en', options);
  return d.charAt(0).toUpperCase().concat(d.slice(1));
}

export function getDayNumberFromDate(date) {
  let d = null;
  if (date) {
    d = new Date(date);
  } else {
    d = new Date();
  }
  return d.getDay();
}

export function getMonthFromDate(date, lang) {
  const options = { month: 'long' };
  let d = null;
  if (date) {
    d = new Date(date);
  } else {
    d = new Date();
  }
  d = d.toLocaleDateString(lang || 'en', options);
  return d.charAt(0).toUpperCase().concat(d.slice(1));
}

export function getMonthNumberFromDate(date) {
  let d = null;
  if (date) {
    d = new Date(date);
  } else {
    d = new Date();
  }
  return d.getMonth()+1;
}

export function getDayOfYear(date) {
  let d = null;
  if (date) {
    d = new Date(date);
  } else {
    d = new Date();
  }
  const timestmp = new Date().setFullYear(new Date().getFullYear(), 0, 1);
  const yearFirstDay = Math.floor(timestmp / 86400000);
  const today = Math.ceil(d.getTime()/ 86400000);
  const dayOfYear = today - yearFirstDay;
  return dayOfYear;
}

export function pad(n) {
  return n < 10 && `0${n}` || n;
}

export function getSameTime(dateToChange, dateWithTime) {
  const returnDate = typeof dateToChange === 'string';
  if (typeof dateWithTime === 'string') {
    dateWithTime = new Date(dateWithTime);
  }
  if (typeof dateToChange === 'string') {
    dateToChange = new Date(dateToChange);
  }
  dateToChange.setHours(dateWithTime.getHours());
  dateToChange.setMinutes(dateWithTime.getMinutes());
  dateToChange.setSeconds(dateWithTime.getSeconds());
  return returnDate && dateToChange || toRFC3339(dateToChange);
}

export function areDatesOnSameDay(firstDate, secondDate) {
  if (typeof firstDate === 'string') {
    firstDate = new Date(firstDate);
  }
  if (typeof secondDate === 'string') {
    secondDate = new Date(secondDate);
  }
  return firstDate.getFullYear() === secondDate.getFullYear() &&
  firstDate.getMonth() === secondDate.getMonth() &&
  firstDate.getDate() === secondDate.getDate();
}
