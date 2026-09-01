import {shallowMount} from '@vue/test-utils';

import * as remoteEventConnector from '../../main/webapp/vue-app/agenda-common/js/RemoteEventConnector.js';
import AgendaConnectToRemoteButton
  from '../../main/webapp/vue-app/agenda-common/components/remote-event/AgendaConnectToRemoteButton.vue';
import AgendaConnectorsDrawer
  from '../../main/webapp/vue-app/agenda-common/components/remote-event/AgendaConnectorsDrawer.vue';
import AgendaUserConnectorSettings
  from '../../main/webapp/vue-app/agenda-user-setting/components/AgendaUserConnectorSettings.vue';

/*
 * EXO-89900 — managed mode, agenda side. When the instance chose the user's
 * CalDAV server, no connect and no disconnect affordance is offered anywhere;
 * status stays. What these pin is each affordance disappearing when managed
 * and coming BACK when not — a guard that hides everything unconditionally
 * passes half of that, and half is how a feature ships broken.
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

describe('AgendaConnectToRemoteButton under managed mode', () => {

  /**
   * Mounts the toolbar button.
   *
   * @param {Array} connectors the descriptors agenda loaded
   * @param {Object} props the per-placement action flags
   * @returns {Object} the wrapper
   */
  function mountButton(connectors, props) {
    return shallowMount(AgendaConnectToRemoteButton, {
      propsData: Object.assign({connectors}, props || {}),
      mocks: {
        $t: key => key,
        $remoteEventConnector: remoteEventConnector,
      },
    });
  }

  /*
   * The connect variant, at every placement that offers it — the header, the
   * left panel, the timeline all mount this one component.
   */
  it('offers connecting when unmanaged and not at all when managed', () => {
    const unmanaged = mountButton([caldavConnector({})]);
    expect(unmanaged.vm.showButton).toBe(true);
    expect(unmanaged.findAll('v-btn').length).toBeGreaterThan(0);

    const managed = mountButton([caldavConnector({managed: true, managedServerName: 'Bluemind'})]);
    expect(managed.vm.showButton).toBe(false);
    expect(managed.findAll('v-btn').length).toBe(0);
  });

  /*
   * The manage variant, which the header and the left panel render for a
   * connected account. It opens the same CalDAV-filtered connect drawer, so it
   * is the same affordance wearing another tooltip.
   */
  it('offers managing a connected account when unmanaged and not at all when managed', () => {
    const connected = {connected: true, isSignedIn: true, user: 'mary@example.org'};

    const unmanaged = mountButton([caldavConnector(connected)],
                                  {showManageAction: true, showToggleAction: false});
    expect(unmanaged.vm.showButton).toBe(true);

    const managed = mountButton([caldavConnector(Object.assign({managed: true}, connected))],
                                {showManageAction: true, showToggleAction: false});
    expect(managed.vm.showButton).toBe(false);
  });

  /*
   * The show/hide-remote-events toggle SURVIVES. It is a view preference over
   * events that are already on screen, not a connect or disconnect affordance,
   * and the timeline header is the only place that offers it — suppressing the
   * whole button would have taken it away from a managed user with nothing to
   * gain.
   */
  it('keeps the show-remote-events toggle for a managed user', () => {
    const managed = mountButton([caldavConnector({managed: true, connected: true, isSignedIn: true, user: 'mary'})],
                                {showToggleAction: true, showManageAction: false});

    expect(managed.vm.showButton).toBe(true);
  });

  /*
   * Managed mode governs the CalDAV family only: a Google account is still the
   * user's to connect.
   */
  it('leaves a non-CalDAV connector alone', () => {
    const wrapper = mountButton([
      caldavConnector({managed: false}),
      {name: 'agenda.googleCalendar', isCaldav: false, enabled: true, canConnect: true},
    ]);

    expect(wrapper.vm.caldavManaged).toBe(false);
    expect(wrapper.vm.showButton).toBe(true);
  });
});

describe('AgendaConnectorsDrawer under managed mode', () => {

  /**
   * Mounts the connectors drawer.
   *
   * @param {Array} connectors the descriptors agenda loaded
   * @returns {Object} the wrapper
   */
  function mountDrawer(connectors) {
    return shallowMount(AgendaConnectorsDrawer, {
      propsData: {connectors},
      mocks: {
        $t: key => key,
        $remoteEventConnector: remoteEventConnector,
      },
      stubs: {
        'exo-drawer': {
          template: '<div><slot name="title"></slot><slot name="content"></slot></div>',
        },
        'exo-confirm-dialog': true,
        'agenda-connector-avatar': true,
      },
    });
  }

  /**
   * The labels of the buttons a row actually offers.
   *
   * Read off the buttons rather than off the drawer's whole text, which also
   * carries its title — and `agenda.connectYourPersonalAgenda` contains
   * `agenda.connect` as a substring, so a text assertion would have passed on
   * the title alone and pinned nothing.
   *
   * @param {Object} wrapper the mounted drawer
   * @returns {Array} one label per rendered button
   */
  function actionLabels(wrapper) {
    return wrapper.findAll('v-btn').wrappers.map(button => button.text().trim());
  }

  /*
   * Defence in depth: the affordances that lead here are already gone, and
   * this is what makes the drawer safe if one is ever reached another way.
   */
  it('offers Connect on an unmanaged row and nothing on a managed one', () => {
    expect(actionLabels(mountDrawer([caldavConnector({})]))).toContain('agenda.connect');

    expect(actionLabels(mountDrawer([caldavConnector({managed: true})]))).toEqual([]);
  });

  /*
   * Disconnect specifically, which matters more than Connect: a managed
   * account is not the user's to break, and one click on it would leave them
   * with no way back — the Connect button that would have fixed it is exactly
   * what managed mode also takes away.
   *
   * The two buttons are independent conditions and not an if/else for this
   * reason: an else would have turned a hidden Disconnect into an offered
   * Connect, which is the opposite of what managed mode means.
   */
  it('hides Disconnect on a managed row without falling back to Connect', () => {
    const connected = {connected: true, isSignedIn: true, user: 'mary@example.org'};

    expect(actionLabels(mountDrawer([caldavConnector(connected)]))).toEqual(['agenda.disconnect']);

    // Neither button, and specifically NOT the Connect one: an if/else would
    // have turned the hidden Disconnect into an offered Connect.
    expect(actionLabels(mountDrawer([caldavConnector(Object.assign({managed: true}, connected))]))).toEqual([]);
  });

  /*
   * Sync now is status, not control, and it stays — the row goes on saying
   * when the account last synchronised and letting the user ask for a run.
   */
  it('keeps Sync now on a managed row', () => {
    const managed = mountDrawer([caldavConnector({
      managed: true,
      connected: true,
      isSignedIn: true,
      user: 'mary@example.org',
      sync: () => Promise.resolve(),
    })]);

    expect(managed.vm.canSync(managed.vm.enabledConnectors[0])).toBe(true);
  });
});

describe('AgendaUserConnectorSettings under managed mode', () => {

  /**
   * Mounts the My Calendars settings section.
   *
   * @param {Array} connectors the descriptors the page loaded
   * @returns {Object} the wrapper
   */
  function mountSettings(connectors) {
    return shallowMount(AgendaUserConnectorSettings, {
      propsData: {connectors, settings: {}, nestedSections: []},
      mocks: {
        $t: (key, args) => args && `${key}(${args[0]})` || key,
        $remoteEventConnector: remoteEventConnector,
      },
    });
  }

  /*
   * The section stops hiding itself. It is where sync-now and the last-sync
   * phrase survive, so a managed user with nothing connected yet would
   * otherwise have no account page at all.
   */
  it('shows when managed even with nothing connected, and hides when neither', () => {
    expect(mountSettings([caldavConnector({managed: true, managedServerName: 'Bluemind'})]).vm.displayed).toBe(true);
    expect(mountSettings([caldavConnector({})]).vm.displayed).toBe(false);
    expect(mountSettings([caldavConnector({connected: true, user: 'mary@example.org'})]).vm.displayed).toBe(true);
  });

  /*
   * Assigned, not provisioned — stated rather than papered over. The server is
   * named, and there is deliberately no last sync beside it, because there is
   * no account yet to have synchronised.
   */
  it('names the server, with no last sync, for a managed user who is not connected', () => {
    const wrapper = mountSettings([caldavConnector({managed: true, managedServerName: 'Bluemind'})]);

    expect(wrapper.vm.accountLine).toBe('agenda.settings.myCalendarsManaged(Bluemind)');
    expect(wrapper.vm.lastSyncLabel).toBe('');
    expect(wrapper.vm.syncableConnector).toBeNull();
  });

  /*
   * A hand-made connection that predates managed mode keeps telling the truth
   * of ITS OWN account: it is synced with that account, not with the server
   * the instance later chose, and saying otherwise would state something false
   * about a connection nothing has touched.
   */
  it('keeps naming the account of a connection made by hand before the mode', () => {
    const wrapper = mountSettings([caldavConnector({
      managed: true,
      managedServerName: 'Bluemind',
      connected: true,
      isSignedIn: true,
      user: 'mary@stalwart.example.org',
    })]);

    expect(wrapper.vm.accountLine).toBe('agenda.settings.myCalendarsSyncedWith(mary@stalwart.example.org)');
  });

  /*
   * The pencil leads to the connect drawer, so it is a control: it goes when
   * managed and comes back when not.
   */
  it('drops the manage pencil when managed and keeps it when not', () => {
    const connected = {connected: true, isSignedIn: true, user: 'mary@example.org'};

    const unmanaged = mountSettings([caldavConnector(connected)]);
    expect(unmanaged.vm.caldavManaged).toBe(false);
    // Found by its title, because the pencil's only text is its icon glyph.
    expect(unmanaged.find('[title="agenda.settings.myCalendarsManage"]').exists()).toBe(true);

    const managed = mountSettings([caldavConnector(Object.assign({managed: true}, connected))]);
    expect(managed.vm.caldavManaged).toBe(true);
    expect(managed.find('[title="agenda.settings.myCalendarsManage"]').exists()).toBe(false);
  });
});
