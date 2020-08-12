import Agenda from './components/Agenda.vue';
import AgendaHeader from './components/AgendaHeader.vue';

const components = {
  'agenda': Agenda,
  'agenda-header': AgendaHeader
};

for (const key in components) {
  Vue.component(key, components[key]);
}
