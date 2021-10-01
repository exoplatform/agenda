// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

import Agenda from './components/Agenda.vue';

const components = {
  'agenda': Agenda,
};

for (const key in components) {
  Vue.component(key, components[key]);
}