// eslint-disable-next-line
export function getEvents(query, ownerId, start, end) {
  // TODO user 'params' in query
//  const params = $.param({
//    query: query || '',
//    ownerId: ownerId,
//    start: start,
//    end: end
//  });
  return fetch('http://www.json-generator.com/api/json/get/cbniKiTuPS?indent=2', {
    method: 'GET',
    //    FIXME credentials: 'include',
  }).then((resp) => {
    if (resp && resp.ok) {
      return resp.json();
    } else {
      throw new Error('Error getting event list');
    }
  });
}