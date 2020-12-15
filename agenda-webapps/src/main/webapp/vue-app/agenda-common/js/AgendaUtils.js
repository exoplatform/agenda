import TIMEZONE_IDS from '../json/timezones.json';

export const USER_TIMEZONE_ID = new window.Intl.DateTimeFormat().resolvedOptions().timeZone;
export const TIMEZONES = [];

export function getUserTimezone() {
  const timeZoneOffset = - (new Date().getTimezoneOffset());
  let timezoneHours = timeZoneOffset / 60;
  let timezoneMinutes = timeZoneOffset % 60;
  timezoneHours = timezoneHours < 10 ? `0${timezoneHours}` : timezoneHours;
  timezoneMinutes = timezoneMinutes < 10 ? `0${timezoneMinutes}` : timezoneMinutes;
  const timezoneSign = timeZoneOffset >= 0 ? '+' : '-';
  return `${timezoneSign}${timezoneHours}:${timezoneMinutes}`;
}

export function getTimeZones() {
  if (TIMEZONES.length) {
    return TIMEZONES;
  }
  const dateObj = new Date(0);
  TIMEZONE_IDS.forEach((timeZone) => {
    const dateFormat = new Intl.DateTimeFormat(eXo.env.portal.language, {
      timeZoneName: 'long',
      second: 'numeric',
      timeZone: timeZone,
    });
    const timeZoneName = dateFormat.format(dateObj);
    TIMEZONES.push({
      value: timeZone,
      text: timeZoneName.charAt(2).toUpperCase() + timeZoneName.substring(3, timeZoneName.length) + ` (${timeZone})`,
    });
  });
  return TIMEZONES;
}

export function convertVuetifyRangeToPeriod(range) {
  const rangeStartHour = range.start.hour < 10 ? `0${range.start.hour}` : range.start.hour;
  const rangeStartMinute = range.start.minute < 10 ? `0${range.start.minute}` : range.start.minute;
  const rangeEndHour = range.end.hour < 10 ? `0${range.end.hour}` : range.end.hour;
  const rangeEndMinute = range.end.minute < 10 ? `0${range.end.minute}` : range.end.minute;
  return {
    start: `${range.start.date}T${rangeStartHour}:${rangeStartMinute}:00.000`,
    end: `${range.end.date}T${rangeEndHour}:${rangeEndMinute}:00.000`,
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
  return Math.ceil((firstThursday - date.valueOf()) / 604800000) + 1;
}

export function toVuetifyDate(date) {
  return `${date.getFullYear()}-${ 
    pad(date.getMonth() + 1)}-${ 
    pad(date.getDate())} ${
    pad(date.getHours())}:${ 
    pad(date.getMinutes())}`;
}

export function toRFC3339(date, ignoreTime, useTimeZone) {
  if (!date) {
    return null;
  }
  if (typeof date === 'number') {
    date = new Date(date);
  } else if (typeof date === 'string') {
    if (date.indexOf('T') === 10 && date.length > 19) {
      date = date.substring(0, 19);
    }
    date = new Date(date);
  }
  let formattedDate; 
  if (ignoreTime) {
    formattedDate = `${date.getFullYear()  }-${
      pad(date.getMonth() + 1)  }-${ 
      pad(date.getDate())  }T00:00:00`;
  } else {
    formattedDate = `${date.getFullYear()  }-${
      pad(date.getMonth() + 1)  }-${ 
      pad(date.getDate())  }T${ 
      pad(date.getHours())  }:${ 
      pad(date.getMinutes())  }:${ 
      pad(date.getSeconds())  
    }`;
  }
  if (useTimeZone) {
    return `${formattedDate}${getUserTimezone()}`;
  }
  return formattedDate;
}

export function toDate(date) {
  if (!date) {
    return null;
  } else if (typeof date === 'number') {
    return new Date(date);
  } else if (typeof date === 'string') {
    if (date.indexOf('T') === 10 && date.length > 19) {
      date = date.substring(0, 19);
    }
    return new Date(date);
  } else if (typeof date === 'object') {
    return new Date(date);
  }
}
export function convertDates(event) {
  event.startDate = event.start && this.toDate(event.start) || null;
  event.endDate = event.end && this.toDate(event.end) || null;
  return event;
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
    d = toDate(date);
  } else {
    d = new Date();
  }
  return d.toLocaleDateString(lang || 'en', options);
}

const DAYS_ABBREVIATIONS = ['SU', 'MO','TU','WE','TH','FR', 'SA'];

export function getDayNameFromDayAbbreviation(day, lang) {
  const date = new Date(1584226800000);
  const dayNum = DAYS_ABBREVIATIONS.indexOf(day);
  date.setDate(dayNum + 1);
  return date.toLocaleDateString(lang || 'en', {weekday: 'long'});
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
  return d.toLocaleDateString(lang || 'en', options);
}

export function getMonthNameFromMonthNumber(monthNumber, lang) {
  const options = { month: 'long' };
  const d = new Date();
  d.setMonth(monthNumber);
  return d.toLocaleDateString(lang || 'en', options);
}

export function getMonthNumberFromDate(date) {
  let d = null;
  if (date) {
    d = new Date(date);
  } else {
    d = new Date();
  }
  return d.getMonth() + 1;
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
  if (firstDate === secondDate) {
    return true;
  }
  if (!firstDate || !secondDate) {
    return true;
  }
  if (!(typeof firstDate === 'object')) {
    firstDate = new Date(firstDate);
  }
  if (!(typeof secondDate === 'object')) {
    secondDate = new Date(secondDate);
  }
  return firstDate.getFullYear() === secondDate.getFullYear() &&
  firstDate.getMonth() === secondDate.getMonth() &&
  firstDate.getDate() === secondDate.getDate();
}

export function getWeekSequenceFromDay(day) {
  switch (day) {
  case 'MO':
    return [1, 2, 3, 4, 5, 6, 0];
  case 'TU':
    return [2, 3, 4, 5, 6, 0, 1];
  case 'WE':
    return [3, 4, 5, 6, 0, 1, 2];
  case 'TH':
    return [4, 5, 6, 0, 1, 2, 3,];
  case 'FR':
    return [5, 6, 0, 1, 2, 3, 4];
  case 'SA':
    return [6, 0, 1, 2, 3, 4, 5];
  case 'SU':
    return [0, 1, 2, 3, 4, 5, 6];
  default:
    return [1, 2, 3, 4, 5, 6, 0];
  }
}

export function areSameObjects(object1, object2) {
  const keys1 = Object.keys(object1);
  const keys2 = Object.keys(object2);
  if (keys1.length !== keys2.length) {
    return false;
  }
  for (const key of keys1) {
    const val1 = object1[key] && typeof object1[key] === 'object' ? JSON.stringify(object1[key]) : object1[key];
    const val2 = object2[key] && typeof object2[key] === 'object' ? JSON.stringify(object2[key]) : object2[key];
    if (val1 !== val2) {
      return false;
    }
  }
  return true;
}

export function isShortEvent(event) {
  return Math.floor(toDate(event.endDate).getTime() / 60000) - Math.floor(toDate(event.startDate).getTime() / 60000) < 60;
}

export function addOpacity(hexColor, opacity) {
  hexColor = hexColor.replace('#', '');
  const duplicate = hexColor.length < 6;
  const rHex = duplicate && `${hexColor[0]}${hexColor[0]}` || `${hexColor[0]}${hexColor[1]}`;
  const gHex = duplicate && `${hexColor[1]}${hexColor[1]}` || `${hexColor[2]}${hexColor[3]}`;
  const bHex = duplicate && `${hexColor[2]}${hexColor[2]}` || `${hexColor[4]}${hexColor[5]}`;
  const r = parseInt(rHex, 16);
  const g = parseInt(gHex, 16);
  const b = parseInt(bHex, 16);
  return `rgba(${r},${g},${b},${opacity / 100})`;
}
