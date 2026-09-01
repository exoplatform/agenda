import {shallowMount} from '@vue/test-utils';

import * as remoteEventConnector from '../../main/webapp/vue-app/agenda-common/js/RemoteEventConnector.js';
import AgendaConnectToRemoteButton
  from '../../main/webapp/vue-app/agenda-common/components/remote-event/AgendaConnectToRemoteButton.vue';
import AgendaConnectorsDrawer
  from '../../main/webapp/vue-app/agenda-common/components/remote-event/AgendaConnectorsDrawer.vue';
import AgendaUserConnectorSettings
  from '../../main/webapp/vue-app/agenda-user-setting/components/AgendaUserConnectorSettings.vue';
import AgendaUserPushSettings
  from '../../main/webapp/vue-app/agenda-user-setting/components/AgendaUserPushSettings.vue';
import AgendaConnector
  from '../../main/webapp/vue-app/agenda-common/components/connector/AgendaConnector.vue';

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
   * The whole section goes under managed mode — this is the case Benjamin saw
   * on the rig: managed mode on, a connection he had made by hand before it,
   * and the row still reading "Synced both ways with <his address> ·
   * Synchronised just now" beside a sync button. Every one of those lines is
   * addressed to somebody who could act on it, and he could not.
   *
   * Written as three separate cases because the middle one is the whole
   * point: `connected` alone — a plain revert — passes the first and third and
   * fails only this one.
   */
  it('does not show under managed mode, connected or not', () => {
    // Managed, nothing connected: nothing to describe and nothing to offer.
    expect(mountSettings([caldavConnector({managed: true})]).vm.displayed).toBe(false);
    // Managed AND connected by hand before the mode - the rig case.
    expect(mountSettings([caldavConnector({
      managed: true,
      connected: true,
      isSignedIn: true,
      user: 'anais.francois@demo3.livecollab.fr',
    })]).vm.displayed).toBe(false);
    // Unmanaged and connected: unchanged, the section is exactly as it was.
    expect(mountSettings([caldavConnector({connected: true, user: 'mary@example.org'})]).vm.displayed).toBe(true);
  });

  /*
   * And nothing of it reaches the page: not the account address, not a server
   * name, not the last-sync phrase, not the sync button. Asserted on the
   * rendered output rather than on `displayed` alone, because what was
   * complained about was what could be READ on the screen.
   */
  it('renders none of the account, the sync state or the sync button when managed', () => {
    const managed = mountSettings([caldavConnector({
      managed: true,
      connected: true,
      isSignedIn: true,
      user: 'anais.francois@demo3.livecollab.fr',
      sync: () => Promise.resolve(),
      lastSynchronised: () => Promise.resolve(new Date()),
    })]);

    expect(managed.text()).toBe('');
    expect(managed.find('[title="agenda.connectors.syncNow"]').exists()).toBe(false);
    expect(managed.find('[title="agenda.settings.myCalendarsManage"]').exists()).toBe(false);
  });

  /*
   * Unmanaged, the section is untouched by this delivery: the account line,
   * the sync button and the manage pencil are all where they were.
   */
  it('leaves the unmanaged section exactly as it was', () => {
    const unmanaged = mountSettings([caldavConnector({
      connected: true,
      isSignedIn: true,
      user: 'mary@example.org',
      sync: () => Promise.resolve(),
    })]);

    expect(unmanaged.vm.caldavManaged).toBe(false);
    expect(unmanaged.text()).toContain('agenda.settings.myCalendarsSyncedWith(mary@example.org)');
    expect(unmanaged.find('[title="agenda.connectors.syncNow"]').exists()).toBe(true);
    // Found by its title, because the pencil's only text is its icon glyph.
    expect(unmanaged.find('[title="agenda.settings.myCalendarsManage"]').exists()).toBe(true);
  });
});

describe('the copy-meetings control under managed mode', () => {

  /** A connected CalDAV account able to receive copies. */
  const pushingAccount = {connected: true, isSignedIn: true, user: 'mary@example.org', canPush: true};

  /**
   * Mounts the "Copy eXo meetings to your calendar account" row.
   *
   * @param {Object} settings the agenda user settings the page loaded
   * @param {Array} connectors the descriptors the page loaded
   * @returns {Object} the wrapper
   */
  function mountPushRow(settings, connectors) {
    return shallowMount(AgendaUserPushSettings, {
      propsData: {settings, connectors},
      mocks: {
        $t: (key, args) => args && `${key}(${args[0]})` || key,
        $remoteEventConnector: remoteEventConnector,
        $settingsService: {saveUserSettings: jest.fn(() => Promise.resolve())},
      },
    });
  }

  /*
   * The control is not status, it is the switch deciding whether the
   * synchronisation happens at all. Left visible, a user switches it off and
   * the administration row's "Everyone synchronises with {server}" becomes a
   * false statement.
   */
  it('is absent when managed and present when not', () => {
    const stored = {automaticPushEvents: true};

    expect(mountPushRow(stored, [caldavConnector(pushingAccount)]).vm.displayed).toBe(true);
    expect(mountPushRow(stored, [caldavConnector(Object.assign({managed: true}, pushingAccount))]).vm.displayed)
      .toBe(false);
  });

  /*
   * And the destination sentence goes with the row: it names a calendar the
   * user cannot change, on a row they cannot act on.
   */
  it('renders nothing at all when managed', async () => {
    const managed = mountPushRow({automaticPushEvents: true},
                                 [caldavConnector(Object.assign({managed: true}, pushingAccount))]);
    await managed.vm.$nextTick();

    expect(managed.text()).toBe('');
  });

  /*
   * The switch's own state still tracks the STORED preference, because that is
   * what has to come back when managed mode is switched off. It positions a
   * control that does not render, so it never contradicts the effective value.
   */
  it('keeps tracking the stored preference behind the hidden control', () => {
    const optedOut = mountPushRow({automaticPushEvents: false},
                                  [caldavConnector(Object.assign({managed: true}, pushingAccount))]);

    expect(optedOut.vm.pushEnabled).toBe(false);
  });

  /*
   * The write path is never taken by managed mode itself: nothing in this
   * delivery calls saveUserSettings, so no stored preference is rewritten
   * behind a user's back.
   */
  it('never writes the setting on its own', () => {
    const wrapper = mountPushRow({automaticPushEvents: false},
                                 [caldavConnector(Object.assign({managed: true}, pushingAccount))]);

    expect(wrapper.vm.$settingsService.saveUserSettings).not.toHaveBeenCalled();
  });
});

describe('copyMeetingsEnabled', () => {

  /*
   * The override, at the point the value is READ. This is the pin that matters
   * most: hiding the control while a stored false still suppressed the copy
   * would produce exactly the silently non-synchronising population the change
   * exists to prevent.
   */
  it('is true under managed mode even when the stored preference is false', () => {
    expect(remoteEventConnector.copyMeetingsEnabled(
      {automaticPushEvents: false},
      [caldavConnector({managed: true})])).toBe(true);
  });

  /*
   * And the stored preference is what governs again the moment managed mode
   * goes: the override lives where the value is read, so nothing has to be
   * restored because nothing was overwritten.
   */
  it('gives the stored preference back when managed mode is switched off', () => {
    const storedOff = {automaticPushEvents: false};
    const connectors = [caldavConnector({managed: true})];

    expect(remoteEventConnector.copyMeetingsEnabled(storedOff, connectors)).toBe(true);

    // The same settings object, unmutated, once the descriptors stop being
    // stamped - which is all that switching managed mode off changes.
    expect(storedOff.automaticPushEvents).toBe(false);
    expect(remoteEventConnector.copyMeetingsEnabled(storedOff, [caldavConnector({})])).toBe(false);
  });

  /*
   * The override reads the settings object and never writes it. A mutant that
   * rewrites the stored preference instead of overriding the read has to fail
   * here.
   */
  it('does not mutate the stored settings', () => {
    const stored = {automaticPushEvents: false, other: 'untouched'};

    remoteEventConnector.copyMeetingsEnabled(stored, [caldavConnector({managed: true})]);

    expect(stored).toEqual({automaticPushEvents: false, other: 'untouched'});
  });

  /*
   * Unmanaged, the function is exactly the expression the push path always
   * used - absent settings mean no copy. The settings screen defaults an unset
   * preference the other way; those two have always differed, and unifying
   * them here would change what an unmanaged deployment does.
   */
  it('leaves the unmanaged behaviour exactly as it was', () => {
    expect(remoteEventConnector.copyMeetingsEnabled({automaticPushEvents: true}, [caldavConnector({})])).toBe(true);
    expect(remoteEventConnector.copyMeetingsEnabled({automaticPushEvents: false}, [caldavConnector({})])).toBe(false);
    expect(remoteEventConnector.copyMeetingsEnabled(null, [caldavConnector({})])).toBe(false);
    expect(remoteEventConnector.copyMeetingsEnabled(null, null)).toBe(false);
  });
});

/*
 * The push path's own call site. copyMeetingsEnabled being right is worth
 * nothing if shouldReachAccount — the single gate every push trigger funnels
 * through — still reads the raw setting, so the wiring is pinned here and not
 * only the helper.
 *
 * The methods are invoked against a hand-made receiver rather than a mounted
 * component: AgendaConnector carries no template, so there is nothing to
 * mount, and what is under test is a decision and not a rendering.
 */
describe('AgendaConnector.shouldReachAccount under managed mode', () => {

  /** A meeting owned by a space, which is the case the copy setting governs. */
  const spaceEvent = {calendar: {owner: {providerId: 'space'}}};

  /**
   * Calls the gate with the settings and descriptors a page would hold.
   *
   * @param {Object} settings the agenda user settings
   * @param {Array} connectors the descriptors agenda loaded
   * @returns {Boolean} whether the account receives the copy
   */
  function reaches(settings, connectors) {
    const receiver = {
      settings,
      connectors,
      $remoteEventConnector: remoteEventConnector,
      isSpaceEvent: AgendaConnector.methods.isSpaceEvent,
    };
    return AgendaConnector.methods.shouldReachAccount.call(receiver, connectors[0], spaceEvent);
  }

  it('copies a space meeting when managed, whatever the stored preference says', () => {
    const managed = [caldavConnector({managed: true, connected: true, canPush: true})];

    expect(reaches({automaticPushEvents: false}, managed)).toBe(true);
    expect(reaches({automaticPushEvents: true}, managed)).toBe(true);
  });

  it('still honours the stored preference when unmanaged', () => {
    const unmanaged = [caldavConnector({connected: true, canPush: true})];

    expect(reaches({automaticPushEvents: false}, unmanaged)).toBe(false);
    expect(reaches({automaticPushEvents: true}, unmanaged)).toBe(true);
  });

  /*
   * The per-account opt-out is a different switch and managed mode does not
   * touch it: an account explicitly excluded stays excluded.
   */
  it('leaves the per-account opt-out alone', () => {
    const managedButOptedOut = [caldavConnector({managed: true, connected: true, canPush: true, pushEnabled: false})];

    expect(reaches({automaticPushEvents: true}, managedButOptedOut)).toBe(false);
  });
});
