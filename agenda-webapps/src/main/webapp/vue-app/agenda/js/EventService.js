
export function getEventList() {
    return new Promise((resolve,reject) => {
        fetch('http://www.json-generator.com/api/json/get/cbniKiTuPS?indent=2', {
            method: 'GET',
        }).then((resp) => {
            if (resp && resp.ok) {
                resolve(resp.json());
            } else {
                reject(Error('Error getting event list'));
            }
        });
    })
}