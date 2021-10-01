// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

import AgendaTimelineWidget from './components/AgendaTimelineWidget.vue';
import AgendaTimelineHeader from './components/AgendaTimelineHeader.vue';

const components = {
  'agenda-timeline-widget': AgendaTimelineWidget,
  'agenda-timeline-header': AgendaTimelineHeader,
};

for (const key in components) {
  Vue.component(key, components[key]);
}
