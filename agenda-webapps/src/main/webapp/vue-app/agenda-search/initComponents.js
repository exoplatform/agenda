// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

import AgendaSearchCard from './components/AgendaSearchCard.vue';

const components = {
  'agenda-search-card': AgendaSearchCard,
};

for (const key in components) {
  Vue.component(key, components[key]);
}

// get override components if exists
if (extensionRegistry) {
  const components = extensionRegistry.loadComponents('agendaSearchCard');
  if (components && components.length > 0) {
    components.forEach(cmp => {
      Vue.component(cmp.componentName, cmp.componentOptions);
    });
  }
}
