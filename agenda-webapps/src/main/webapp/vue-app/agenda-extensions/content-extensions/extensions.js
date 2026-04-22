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
    execute: async (context, content) => {
      const eventToDelete =  context?.params?.eventToDelete;
      const event = context?.data;
      const existEventId = !!content?.parameters?.['eventId'];
      if (!event && existEventId ) {
        if (eventToDelete) {
          await Vue.prototype.$eventService.deleteEvent(content.parameters['eventId']);
        }
        document.dispatchEvent(new CustomEvent('content-event-removed'));
        return {
          data: { eventId: null },
        };
      }
      if (!event) {
        return;
      }
      event.summary = content.title;
      event.description = generateContentActionLink({
        objectType: 'news',
        objectId: content?.id,
        activityId: content?.activityId,
        iconClass: 'fa-newspaper',
        text: content.title,
      });

      const updateEvent = !!event?.id;
      const eventRequest = updateEvent ? Vue.prototype.$eventService.updateEvent(event)
        : Vue.prototype.$eventService.createEvent(event);
      const savedEvent = await eventRequest;
      document.dispatchEvent(
        new CustomEvent(`content-event-${updateEvent? 'updated': 'created'}`, {
          detail: { event: savedEvent }
        })
      );
      return {
        data: { eventId: savedEvent.id },
      };
    }
  });

  extensionRegistry.registerComponent('ContentDetails', 'content-event-detail', {
    id: 'content-event-reminder',
    vueComponent: Vue.options.components['content-event-display-reminder'],
    rank: 1,
    isEnabled: (params) => {
      return !!params?.eventId;
    }
  });

  document.dispatchEvent(new CustomEvent('content-publication-extensions-updated'));
}

function generateContentActionLink({ objectType, objectId, activityId, iconClass, text }) {
  return `<a 
           data-object="${objectType}:${objectId}" 
           data-content-link="true" contenteditable="false" 
           class="content-link" 
           href="/portal/dw/activity?id=${activityId}">
           <i aria-hidden="true" 
           class="v-icon notranslate fa fa ${iconClass} theme--light icon-default-color" 
           style="font-size: 16px; margin: 0 4px;"></i>
           ${text}
          </a>`;
}
