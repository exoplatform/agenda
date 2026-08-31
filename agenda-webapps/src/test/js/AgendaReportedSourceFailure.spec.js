import * as agendaUtils from '../../main/webapp/vue-app/agenda-common/js/AgendaUtils.js';
import AgendaEventFormDates from '../../main/webapp/vue-app/agenda-common/components/event/form/AgendaEventFormDates.vue';
import Agenda from '../../main/webapp/vue-app/agenda/components/Agenda.vue';
import AgendaTimelineWidget from '../../main/webapp/vue-app/agenda-timeline/components/AgendaTimelineWidget.vue';

/*
 * The regression this file exists for: EXO-89843, "the CalDAV read REST
 * answers 200 with an empty list when the server is unreachable".
 *
 * EXO-89842 gave the three views a per-source failure flag, raised from a
 * REJECTED promise. Measured live with the calendar server stopped, nothing
 * rendered: the read had not rejected. The server caught the failure, logged
 * it, and answered 200 with an empty array, so the promise resolved and there
 * was no rejection to catch. A connector reading a third-party server is the
 * only party that can tell "asked and empty" from "could not ask", and it now
 * says so by RESOLVING `{events, failed}` instead of a bare array.
 *
 * The pins below are therefore all about a SUCCESSFUL promise that reports a
 * failure. Every one is written as a contrast between an account that answered
 * `{events: [], failed: false}` and one that answered `{events: [], failed:
 * true}`: identical event lists, opposite failure states. A mutant that drops
 * the flag makes the two cases identical and fails on the assertion, not on an
 * exception — the two are indistinguishable outcomes without the contrast.
 */

const REACHABLE = {name: 'caldav', user: 'jane@example.com', rank: 1};

const REPORTING = {name: 'caldav-2', user: 'john@example.com', rank: 2};

/**
 * One event as an account hands it over, before the view converts its dates.
 *
 * @param {String} id the remote event identifier
 * @returns {Object} a remote event
 */
function remoteEvent(id) {
  return {
    id,
    summary: `event ${id}`,
    start: '2026-09-01T09:00:00Z',
    end: '2026-09-01T10:00:00Z',
  };
}

/**
 * An account that resolves — successfully — with what it read and whether the
 * read failed. This is the CalDAV connector's shape.
 *
 * @param {Object} connector the connector descriptor
 * @param {Array} events what the account managed to read
 * @param {Boolean} failed whether the account, or one of its calendars, could
 *          not be read
 * @returns {Object} a signed-in connector stub
 */
function reporting(connector, events, failed) {
  return Object.assign({}, connector, {
    isSignedIn: true,
    getEvents: () => Promise.resolve({events, failed}),
  });
}

/**
 * An account answering the way every other connector does: a bare array, no
 * failure vocabulary at all.
 *
 * @param {Object} connector the connector descriptor
 * @param {Array} events what the account holds
 * @returns {Object} a signed-in connector stub
 */
function answering(connector, events) {
  return Object.assign({}, connector, {
    isSignedIn: true,
    getEvents: () => Promise.resolve(events),
  });
}

/*
 * $agendaUtils is the SHIPPED module and not a stub: reading the connector's
 * answer and splitting answers from failures are both under test.
 */
const baseVm = {
  loading: false,
  remoteEvents: [],
  failedConnectors: [],
  /*
   * EXO-89851 gave the form a second kind of unreadable source — a calendar,
   * beside an account — and the one warning now names both. Declared here so
   * these fixtures keep describing the component as it really is: a `|| []`
   * in the computed would have made them pass while still describing
   * something the component does not hold.
   */
  failedStoreSourceKeys: [],
  $t: key => key,
  period: {start: new Date('2026-09-01T00:00:00Z'), end: new Date('2026-09-08T00:00:00Z')},
  $agendaUtils: agendaUtils,
};

/**
 * Runs a component's shipped remote-events retrieval against a set of
 * accounts and returns the state it left behind.
 *
 * @param {Object} component the component whose method to run
 * @param {Array} connectors the signed-in accounts to ask
 * @returns {Promise} resolves with the resulting vm
 */
function retrieve(component, connectors) {
  const vm = Object.assign({}, baseVm, {
    signedInConnectors: connectors,
    connectorStatus: 1,
    settingsLoaded: true,
    refreshEventsToDisplay: () => {},
  });
  component.methods.retrieveRemoteEvents.call(vm);
  return Promise.resolve().then(() => Promise.resolve()).then(() => Promise.resolve()).then(() => vm);
}

/**
 * Evaluates a component's failure computeds over a vm.
 *
 * @param {Object} component the component whose computeds to evaluate
 * @param {Object} vm the state to evaluate them against
 * @returns {Object} {names, hasFailedSource}
 */
function failureState(component, vm) {
  const names = component.computed.failedSourceNames.call(vm);
  return {
    names,
    hasFailedSource: component.computed.hasFailedSource.call({failedSourceNames: names}),
  };
}

describe('AgendaUtils reads a connector answer that reports its own failure', () => {
  it('accepts the bare array every other connector answers', () => {
    const read = agendaUtils.readConnectorAnswer([remoteEvent('a')]);
    expect(read.events).toHaveLength(1);
    expect(read.failed).toBe(false);
  });

  it('carries the flag a connector sets on a successful answer', () => {
    expect(agendaUtils.readConnectorAnswer({events: [], failed: true}).failed).toBe(true);
    // the contrast: same empty events, and this one is NOT a failure
    expect(agendaUtils.readConnectorAnswer({events: [], failed: false}).failed).toBe(false);
  });

  it('never hands a view an absent events array to iterate', () => {
    expect(agendaUtils.readConnectorAnswer(null).events).toEqual([]);
    expect(agendaUtils.readConnectorAnswer({failed: true}).events).toEqual([]);
  });
});

describe('AgendaUtils keeps a partial read partial', () => {
  it('draws the events a failed account did return, and still names it', () => {
    const split = agendaUtils.splitRemoteEventResults([
      {connector: REACHABLE, events: [remoteEvent('a')]},
      {connector: REPORTING, events: [remoteEvent('b')], failed: true},
    ]);
    // both halves at once: partitioning on `failed` alone would drop event
    // `b` to report the account, which is a worse week than the failure
    expect(split.events.map(event => event.id)).toEqual(['a', 'b']);
    expect(split.failedConnectors).toEqual([REPORTING]);
  });

  it('still drops an account that returned no events array at all', () => {
    const split = agendaUtils.splitRemoteEventResults([
      {connector: REACHABLE, events: [remoteEvent('a')]},
      {connector: REPORTING, failed: true},
    ]);
    expect(split.events).toHaveLength(1);
    expect(split.failedConnectors).toEqual([REPORTING]);
  });
});

describe('the three views hear a failure a resolved promise reported', () => {
  const views = [
    ['the calendar', Agenda],
    ['the timeline widget', AgendaTimelineWidget],
    ['the event form', AgendaEventFormDates],
  ];

  views.forEach(([label, component]) => {
    it(`${label} names an account that answered 200 with nothing it could read`, () => {
      return retrieve(component, [
        answering(REACHABLE, [remoteEvent('a')]),
        reporting(REPORTING, [], true),
      ]).then(vm => {
        const state = failureState(component, vm);
        expect(state.hasFailedSource).toBe(true);
        expect(state.names).toEqual(['john@example.com']);
      });
    });

    it(`${label} stays silent when that same account answered nothing on purpose`, () => {
      return retrieve(component, [
        answering(REACHABLE, [remoteEvent('a')]),
        reporting(REPORTING, [], false),
      ]).then(vm => {
        // identical events to the case above — the flag is the only difference
        expect(vm.remoteEvents).toHaveLength(1);
        expect(failureState(component, vm).hasFailedSource).toBe(false);
      });
    });

    it(`${label} keeps the events of a partially read account and still names it`, () => {
      return retrieve(component, [
        reporting(REPORTING, [remoteEvent('a')], true),
      ]).then(vm => {
        expect(vm.remoteEvents).toHaveLength(1);
        expect(failureState(component, vm).hasFailedSource).toBe(true);
      });
    });
  });
});
