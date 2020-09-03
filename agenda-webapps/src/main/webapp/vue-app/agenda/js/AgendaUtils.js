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
  const rangeEndHour = range.end.hour < 10 ? `0${range.end.hour}` : range.start.hour;
  const rangeEndMinute = range.end.minute < 10 ? `0${range.end.minute}` : range.start.minute;
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

export function getFormattedFileSize(fileSize) {
  let measure;
  const BYTES_IN_KB = 1024;
  const BYTES_IN_GB = 1073741824;
  const MB_IN_GB = 10;
  const BYTES_IN_MB = 1048576;
  const formattedSizePrecision = 2;
  const minGb = MB_IN_GB * BYTES_IN_MB; // equals 0.01 GB
  const minMb = 10000; // equals 0.01 MB, which is the smallest number with precision `formattedSizePrecision`
  const minKb = 10; // equals 0.01 KB, which is the smallest number with precision `formattedSizePrecision`
  let size = fileSize;
  if (fileSize < minKb) {
    measure = ' bytes'; // TODO I18N
  } else if (fileSize < minMb) {
    size = fileSize / BYTES_IN_KB;
    measure = ' kilo'; // TODO I18N
  } else if (fileSize < minGb) {
    size = fileSize / BYTES_IN_MB;
    measure = ' mega'; // TODO I18N
  } else {
    size = fileSize / BYTES_IN_GB;
    measure = ' giga'; // TODO I18N
  }
  return (+size).toFixed(formattedSizePrecision).concat(measure);
}

export function toVuetifyDate(date) {
  return `${date.getFullYear()}-${ 
    pad(date.getMonth() + 1)}-${ 
    pad(date.getDate())} ${
    pad(date.getHours())}:${ 
    pad(date.getMinutes())}`;
}

export function toRFC3339(date) {
  return `${date.getFullYear()  }-${ 
    pad(date.getMonth() + 1)  }-${ 
    pad(date.getDate())  }T${ 
    pad(date.getHours())  }:${ 
    pad(date.getMinutes())  }:${ 
    pad(date.getSeconds())  
  }${getUserTimezone()}`;
}

function pad(n) {
  return n < 10 && `0${n}` || n;
}