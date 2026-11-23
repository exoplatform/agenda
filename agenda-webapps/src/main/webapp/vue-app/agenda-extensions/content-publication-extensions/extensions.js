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
      // An address entered (Enter or space) just before the save carries its
      // participant once the platform answered whose address it is (US06)
      await Vue.prototype.$eventService.typedAddressLookupsSettled();
      // A closing of an open event with a limit was confirmed at its padlock
      const eventRequest = updateEvent ? Vue.prototype.$eventService.updateEvent(event, context?.params?.confirmClose)
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

/**
 * Escapes a value written into the link, as a double-quoted attribute or as
 * text: the characters that open markup or close the attribute, and only
 * them, so that a value holding none is written unchanged.
 *
 * @param {*} value the value to write, none written as an empty string
 * @returns {string} the escaped value
 */
function escapeHtml(value) {
  return `${value ?? ''}`.replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}

function generateContentActionLink({ objectType, objectId, activityId, iconClass, text }) {
  return `<a 
           data-object="${escapeHtml(objectType)}:${escapeHtml(objectId)}" 
           data-content-link="true" contenteditable="false" 
           class="content-link" 
           href="/portal/dw/activity?id=${escapeHtml(activityId)}">
           <i aria-hidden="true" 
           class="v-icon notranslate fa fa ${escapeHtml(iconClass)} theme--light icon-default-color" 
           style="font-size: 16px; margin: 0 4px;"></i>
           ${escapeHtml(text)}
          </a>`;
}
