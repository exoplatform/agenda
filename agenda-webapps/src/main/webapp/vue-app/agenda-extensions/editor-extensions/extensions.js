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

export function registerExtensions(i18n) {
  extensionRegistry.registerExtension('CustomEditorSetting', 'event', {
    id: 'custom-event-editor',
    rank: 10,
    enabled: () => true,
    getSettings: () => {
      return {
        icon: 'fas fa-calendar-plus',
        titlePlaceholder: i18n.t('contentEditor.event.title.placeholder'),
        bodyPlaceholder: i18n.t('contentEditor.event.description.placeholder')
      };
    }
  });
}
