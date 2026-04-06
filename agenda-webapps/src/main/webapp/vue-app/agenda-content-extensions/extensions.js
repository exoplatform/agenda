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
  extensionRegistry.registerComponent('ContentPublication', 'content-form', {
    id: 'content-event',
    vueComponent: Vue.options.components['content-event-form'],
    rank: 1,
    execute: async (event) => {
      const createdEvent = await Vue.prototype.$eventService.createEvent(event);
      return {
        eventId: createdEvent.id,
      };
    }
  });

  document.dispatchEvent(new CustomEvent('content-publication-extensions-updated'));
}
