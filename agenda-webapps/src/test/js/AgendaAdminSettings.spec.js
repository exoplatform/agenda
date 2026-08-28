import {shallowMount} from '@vue/test-utils';

import AgendaAdminSettings
  from '../../main/webapp/vue-app/agenda-admin-settings/components/AgendaAdminSettings.vue';

/*
 * The regression this file exists for.
 *
 * The admin page used to write its own two sections into the template, above
 * the loop over the contributed ones. EXO-89757 removed that hardcoding so
 * that a contributed section can be ranked BEFORE them — and the cost of
 * getting it wrong is not a misordered page, it is two sections that render
 * for nobody, on every deployment, whether or not the CalDAV add-on is even
 * installed. So the load-bearing test is the first one: no extension
 * registered at all, both built-ins still on the page.
 */

const passthrough = tag => ({
  template: `<${tag}><slot></slot></${tag}>`,
});

const ConnectorSettingsStub = {
  name: 'agenda-admin-connector-settings',
  props: ['settings'],
  template: '<div class="connector-settings-stub"></div>',
};

const EmbedMapSettingsStub = {
  name: 'agenda-admin-embed-map-settings',
  props: ['settings'],
  template: '<div class="embed-map-settings-stub"></div>',
};

/**
 * A section as an add-on contributes it: an id, a rank, and an opaque
 * component object built from its own globally registered component.
 *
 * @param {String} id the extension id
 * @param {Number} rank where the add-on asks to be placed
 * @returns {Object} the extension entry
 */
function contributedSection(id, rank) {
  return {
    id,
    rank,
    vueComponent: {
      name: id,
      props: ['settings'],
      template: `<div class="${id}-stub"></div>`,
    },
  };
}

/**
 * Mounts the page with the extension registry answering a given set of
 * contributed sections.
 *
 * @param {Array} extensions what the registry answers, or undefined to make
 *          it answer nothing at all
 * @returns {Object} the mounted wrapper
 */
function mountPage(extensions) {
  global.extensionRegistry = {
    loadExtensions: () => extensions,
  };
  return shallowMount(AgendaAdminSettings, {
    stubs: {
      'v-app': passthrough('div'),
      'v-main': passthrough('div'),
      'agenda-admin-connector-settings': ConnectorSettingsStub,
      'agenda-admin-embed-map-settings': EmbedMapSettingsStub,
    },
    mocks: {
      $settingsService: {
        getUserSettings: () => Promise.resolve({}),
      },
      $applicationLoaded: () => {},
    },
  });
}

/**
 * The ids of the sections the page decided to render, in the order it decided
 * to render them.
 *
 * @param {Object} wrapper the mounted page
 * @returns {Array} the section ids
 */
function renderedSectionIds(wrapper) {
  return wrapper.vm.sections.map(section => section.id);
}

describe('AgendaAdminSettings sections', () => {
  it('renders its own two sections when no add-on contributed any', () => {
    const wrapper = mountPage([]);

    expect(renderedSectionIds(wrapper)).toEqual(['agendaEmbedMap', 'agendaConnectors']);
    expect(wrapper.find('.connector-settings-stub').exists()).toBe(true);
    expect(wrapper.find('.embed-map-settings-stub').exists()).toBe(true);
  });

  it('renders them just the same when the registry answers nothing at all', () => {
    const wrapper = mountPage(undefined);

    expect(renderedSectionIds(wrapper)).toEqual(['agendaEmbedMap', 'agendaConnectors']);
    expect(wrapper.find('.connector-settings-stub').exists()).toBe(true);
    expect(wrapper.find('.embed-map-settings-stub').exists()).toBe(true);
  });

  it('lets a contributed section rank itself ahead of a built-in one', () => {
    // What the CalDAV registry asks for: rank 20 puts it after the embedded
    // map (10) and before the connectors table (30), which is derived from
    // what the registry holds. Ahead of a built-in is the property being
    // pinned -- no rank could express it while these two were hardcoded.
    const wrapper = mountPage([contributedSection('caldavServers', 20)]);

    expect(renderedSectionIds(wrapper)).toEqual(['agendaEmbedMap', 'caldavServers', 'agendaConnectors']);
    // A contributed component is an opaque object this page never imports, so
    // shallowMount stubs it: it is found by its name, not by a class its own
    // template would have drawn.
    expect(wrapper.findComponent({name: 'caldavServers'}).exists()).toBe(true);
    expect(wrapper.find('.connector-settings-stub').exists()).toBe(true);
    expect(wrapper.find('.embed-map-settings-stub').exists()).toBe(true);
  });

  it('interleaves contributed sections with the built-in ones by rank', () => {
    const wrapper = mountPage([contributedSection('late', 40), contributedSection('middle', 20)]);

    expect(renderedSectionIds(wrapper)).toEqual(['agendaEmbedMap', 'middle', 'agendaConnectors', 'late']);
  });

  it('drops a contributed section carrying no component, keeping the built-in ones', () => {
    const wrapper = mountPage([{id: 'broken', rank: 10}]);

    expect(renderedSectionIds(wrapper)).toEqual(['agendaEmbedMap', 'agendaConnectors']);
    expect(wrapper.find('.connector-settings-stub').exists()).toBe(true);
  });
});
