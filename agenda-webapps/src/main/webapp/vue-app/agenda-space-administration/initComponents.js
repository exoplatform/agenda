/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

import AgendaSpaceAdministration from './components/AgendaSpaceAdministration.vue';
import AgendaCalendarSubscriptionDrawer from '../agenda-common/components/filter/AgendaCalendarSubscriptionDrawer.vue';
import AgendaSpaceSubscriptionsDrawer from '../agenda-common/components/filter/AgendaSpaceSubscriptionsDrawer.vue';

// The drawers managing the calendars a space subscribes to (EXO-90373) are
// imported here rather than through a dependency on AgendaCommon, which would
// load its whole chain on every space-settings page, for every viewer
const components = {
  'agenda-space-administration': AgendaSpaceAdministration,
  'agenda-calendar-subscription-drawer': AgendaCalendarSubscriptionDrawer,
  'agenda-space-subscriptions-drawer': AgendaSpaceSubscriptionsDrawer,
};

for (const key in components) {
  Vue.component(key, components[key]);
}


import * as calendarService from '../agenda-common/js/CalendarService.js';
import * as calendarSubscriptionService from '../agenda-common/js/CalendarSubscriptionService.js';
import * as agendaUtils from '../agenda-common/js/AgendaUtils.js';


if (!Vue.prototype.$calendarService) {
  window.Object.defineProperty(Vue.prototype, '$calendarService', {
    value: calendarService,
  });
}
if (!Vue.prototype.$calendarSubscriptionService) {
  window.Object.defineProperty(Vue.prototype, '$calendarSubscriptionService', {
    value: calendarSubscriptionService,
  });
}
if (!Vue.prototype.$agendaUtils) {
  window.Object.defineProperty(Vue.prototype, '$agendaUtils', {
    value: agendaUtils,
  });
}