import Agenda from './components/Agenda.vue';

const components = {
  'agenda': Agenda,
};

for (const key in components) {
  Vue.component(key, components[key]);
}
