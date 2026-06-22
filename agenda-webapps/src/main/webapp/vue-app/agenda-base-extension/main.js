const timeZoneId = new window.Intl.DateTimeFormat().resolvedOptions().timeZone;
if (eXo.env.portal.userName && eXo.env.portal.userTimezone !== timeZoneId) {
  fetch('/agenda/rest/timezone', {
    headers: {
      'Content-Type': 'text/plain',
    },
    method: 'POST',
    credentials: 'include',
    body: timeZoneId,
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Server Request Error: Cannot update user TimeZone');
    }
  });
}