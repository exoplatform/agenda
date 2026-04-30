/*
 Copyright (C) 2026 eXo Platform SAS.
 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.
 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.
 You should have received a copy of the GNU Affero General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

export function registerExtensions() {
  extensionRegistry.registerExtension('EmbedMapProviders', 'embedMapProviders', {
    id: 'google-maps',
    rank: 20,
    label: 'Google Maps',
    enabled: () => true,
    // eslint-disable-next-line require-await
    async geocode(location) {
      return {coords: null, location};
    },
    mapEmbedUrl({location}) {
      return `https://maps.google.com/maps?q=${encodeURIComponent(location)}&output=embed`;
    },
    mapsUrl({location}) {
      return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(location)}`;
    }
  });

  extensionRegistry.registerExtension('EmbedMapProviders', 'embedMapProviders', {
    id: 'openStreet-map',
    rank: 10,
    label: 'Open Street Maps',
    enabled: () => true,
    async geocode(location, language) {
      const url = `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(location)}&format=json&limit=1`;
      const res = await fetch(url, {headers: {'Accept-Language': language}});
      const data = await res.json();
      if (!data?.length) {
        return null;
      }
      const {lat, lon, boundingbox} = data[0];
      return {
        coords: {lat: parseFloat(lat), lon: parseFloat(lon)},
        bbox: `${parseFloat(boundingbox[2])},${parseFloat(boundingbox[0])},${parseFloat(boundingbox[3])},${parseFloat(boundingbox[1])}`
      };
    },
    mapEmbedUrl({coords, bbox}) {
      return `https://www.openstreetmap.org/export/embed.html?bbox=${bbox}&layer=mapnik&marker=${coords.lat},${coords.lon}`;
    },
    mapsUrl({coords, location}) {
      return coords
        ? `https://www.openstreetmap.org/?mlat=${coords.lat}&mlon=${coords.lon}#map=15/${coords.lat}/${coords.lon}`
        : `https://www.openstreetmap.org/search?query=${encodeURIComponent(location)}`;
    }
  });
}