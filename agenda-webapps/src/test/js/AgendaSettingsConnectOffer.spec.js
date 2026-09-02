import {mount} from '@vue/test-utils';

import * as remoteEventConnector from '../../main/webapp/vue-app/agenda-common/js/RemoteEventConnector.js';
import AgendaUserConnectorSettings
  from '../../main/webapp/vue-app/agenda-user-setting/components/AgendaUserConnectorSettings.vue';

/*
 * EXO-89918 — connecting a first calendar account from the settings page.
 *
 * The section used to hide itself whole when no account was connected, so a
 * user with none and an instance that had imposed none found no way to connect
 * a calendar on the page that carries their e-mail account rows. The affordance
 * existed, as a plug icon beside the "+" of the left panel's Personal header,
 * and a user who does not find it concludes the feature is not there.
 *
 * The four connected × managed cells are pinned together, because the bug was
 * never one cell rendering wrongly: it was one cell nobody had mounted. Three
 * of these already held; the first is the one that did not.
 */

/**
 * A CalDAV connector descriptor as the add-on stamps them.
 *
 * @param {Object} overrides what to change on the default descriptor
 * @returns {Object} the descriptor
 */
function caldavConnector(overrides) {
  return Object.assign({
    name: 'agenda.caldavCalendar.6',
    isCaldav: true,
    enabled: true,
    canConnect: true,
    connected: false,
    isSignedIn: false,
    user: null,
    managed: false,
    managedServerName: null,
  }, overrides);
}

/**
 * A nested row as the CalDAV add-on registers one, drawing a title of its own
 * so the test can see whether the container let it through.
 *
 * @param {String} id the extension id
 * @param {String} title what the row draws
 * @returns {Object} the registered section descriptor
 */
function nestedRow(id, title) {
  // A render function and not a template string: the suite runs against the
  // runtime-only Vue build, which cannot compile a template at runtime.
  return {id, vueComponent: {render: h => h('div', title)}};
}

/**
 * Mounts the My Calendars section.
 *
 * @param {Array} connectors the descriptors agenda loaded
 * @param {Array} nestedSections the rows the CalDAV add-on contributes
 * @returns {Object} the wrapper
 */
function mountSettings(connectors, nestedSections) {
  // mount, not shallowMount: the nested rows arrive through `<component :is>`
  // and a shallow render stubs exactly those away.
  return mount(AgendaUserConnectorSettings, {
    propsData: {connectors, settings: {}, nestedSections: nestedSections || []},
    mocks: {
      $t: (key, args) => args && `${key}(${args[0]})` || key,
      $remoteEventConnector: remoteEventConnector,
    },
  });
}

/** A connected, user-owned account. */
const CONNECTED = {connected: true, isSignedIn: true, user: 'mary@example.org'};

describe('connecting a first calendar account from the settings page', () => {

  /*
   * THE case that was missing. Everything about the account line is absent —
   * there is no account to describe — and so are the nested rows, each of which
   * speaks about one: the device URL is built from the account's own address.
   */
  it('offers connecting when the user has no account and the instance imposed none', () => {
    const wrapper = mountSettings([caldavConnector({})], [nestedRow('caldavDeviceSetup', 'phone setup row')]);

    expect(wrapper.vm.displayed).toBe(true);
    expect(wrapper.vm.connectOffered).toBe(true);
    expect(wrapper.find('[aria-label="agenda.connect"]').exists()).toBe(true);
    expect(wrapper.text()).toContain('agenda.settings.myCalendars');
    expect(wrapper.text()).toContain('agenda.settings.myCalendarsConnectPrompt');

    // nothing that speaks about an account, since there is none
    expect(wrapper.text()).not.toContain('agenda.settings.myCalendarsSyncedWith');
    expect(wrapper.text()).not.toContain('phone setup row');
    expect(wrapper.find('[title="agenda.connectors.syncNow"]').exists()).toBe(false);
    expect(wrapper.find('[title="agenda.settings.myCalendarsManage"]').exists()).toBe(false);
  });

  /*
   * It opens the same CalDAV-filtered drawer the pencil opens: connecting from
   * here must land the user in the connect flow, not in the generic connectors
   * list where CalDAV is deliberately never offered.
   */
  it('opens the CalDAV connect drawer', () => {
    const wrapper = mountSettings([caldavConnector({})]);
    const emitted = [];
    wrapper.vm.$root.$emit = (...args) => emitted.push(args);

    wrapper.find('[aria-label="agenda.connect"]').trigger('click');

    expect(emitted).toEqual([['agenda-connectors-drawer-open', {filter: 'caldav'}]]);
  });

  /*
   * Managed mode: the instance chose the account, so connecting one is exactly
   * what this user cannot do. Same rule the account line already followed,
   * applied before there is an account — and the page stays empty rather than
   * drawing a heading over nothing.
   */
  it('does not offer connecting to a managed user with no account yet', () => {
    const wrapper = mountSettings(
      [caldavConnector({managed: true, managedServerName: 'Bluemind'})],
      [nestedRow('caldavDeviceSetup', 'phone setup row')]);

    expect(wrapper.vm.connectOffered).toBe(false);
    expect(wrapper.vm.displayed).toBe(false);
    expect(wrapper.text()).toBe('');
  });

  /*
   * A managed user WITH an account keeps the nested rows and gains no connect
   * button: unchanged by this delivery, pinned here so the new condition
   * cannot quietly reach this cell.
   */
  it('leaves a managed connected user with the nested rows and no connect button', () => {
    const wrapper = mountSettings(
      [caldavConnector(Object.assign({managed: true}, CONNECTED))],
      [nestedRow('caldavDeviceSetup', 'phone setup row')]);

    expect(wrapper.vm.connectOffered).toBe(false);
    expect(wrapper.vm.displayed).toBe(true);
    expect(wrapper.text()).toContain('phone setup row');
    expect(wrapper.find('[aria-label="agenda.connect"]').exists()).toBe(false);
  });

  /*
   * And a connected user is offered nothing to connect: they have one. The
   * account line answers for the account instead.
   */
  it('offers no connect button to a user who already has an account', () => {
    const wrapper = mountSettings(
      [caldavConnector(CONNECTED)],
      [nestedRow('caldavPendingCopies', 'pending copies row')]);

    expect(wrapper.vm.connectOffered).toBe(false);
    expect(wrapper.find('[aria-label="agenda.connect"]').exists()).toBe(false);
    expect(wrapper.text()).toContain('agenda.settings.myCalendarsSyncedWith(mary@example.org)');
    expect(wrapper.text()).toContain('pending copies row');
  });

  /*
   * With no CalDAV connector enabled at all there is nothing to connect TO,
   * and the drawer this button opens would list nothing. A deployment that
   * enables no CalDAV server is a configuration, not a user's problem to
   * solve.
   */
  it('offers nothing when the deployment enables no CalDAV connector', () => {
    const wrapper = mountSettings([
      {name: 'agenda.googleCalendar', isCaldav: false, enabled: true, canConnect: true},
    ]);

    expect(wrapper.vm.caldavKnown).toBe(false);
    expect(wrapper.vm.displayed).toBe(false);
    expect(wrapper.text()).toBe('');
  });
});
