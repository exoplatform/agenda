export function getUserTimezone() {
  const timezoneHours = eXo.env.portal.timezoneOffset / 3600000;
  const timezoneMinutes = eXo.env.portal.timezoneOffset % 3600000 / 60000;
  const timezoneSign = eXo.env.portal.timezoneOffset >= 0 ? '+' : '-';
  return `${timezoneSign}${timezoneHours}:${timezoneMinutes}`;
}

export function convertVuetifyRangeToPeriod(range, userTimezone) {
  return {
    start: `${range.start.year}-${range.start.month}-${range.start.day}T${range.start.hour}:${range.start.minute}:${range.start.second}.000${userTimezone}`,
    end: `${range.end.year}-${range.end.month}-${range.end.day}T${range.end.hour}:${range.end.minute}:${range.end.second}.000${userTimezone}`,
  };
}