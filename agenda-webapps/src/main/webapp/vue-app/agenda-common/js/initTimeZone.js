import timezones from '../json/timezones.json';
export function initTimeZones() {
  const dateObj = new Date(0);
  const timeZoneChoices = [];
  timezones.forEach((timeZone) => {
    const dateFormat = new Intl.DateTimeFormat(eXo.env.portal.language, {
      timeZoneName: 'long',
      second: 'numeric',
      timeZone: timeZone
    });
    const timeZoneName = dateFormat.format(dateObj);
    timeZoneChoices.push({
      value: timeZone,
      text: timeZoneName.charAt(2).toUpperCase() + timeZoneName.substring(3, timeZoneName.length),
    });
  });
  return timeZoneChoices;
}