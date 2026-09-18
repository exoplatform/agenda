/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import AgendaConnector from '../../main/webapp/vue-app/agenda-common/components/connector/AgendaConnector.vue';

/**
 * Who gets offered the calendar that receives the meeting copies, and on what
 * evidence (EXO-90396).
 *
 * <p>Two independent gates decide it, and the defect needed both to be wrong.
 * The connector says whether there is a calendar to create at all
 * (`canCreateCalendar`, derived per registration in the CalDAV add-on); this
 * component says whether the connected account already has one. A read that
 * FAILED used to answer the second question with "it has none", so a server
 * unreachable for one moment produced an offer to create a calendar the
 * account already held — and, on a server writing into the account's own
 * default calendar, an offer nothing could have carried out.</p>
 *
 * <p><b>Harness.</b> This component renders nothing — it is a behaviour holder
 * whose `created` hook subscribes to the root bus — so the pins call its
 * methods against a hand-built receiver rather than mounting it. That keeps
 * each pin on the one decision it is about; what the methods read off `this`
 * is listed in `receiver` below.</p>
 */
describe('Offering the calendar that receives the copies', () => {

  const OPEN_EVENT = 'agenda-connector-mirror-calendar-open';

  /**
   * A receiver carrying exactly what the methods under test read, with the
   * root bus recorded so a pin can say whether the step was offered.
   *
   * @param {Object} overrides anything the pin wants to state itself
   * @returns {Object} the receiver, its emissions in `emitted`
   */
  function receiver(overrides) {
    const emitted = [];
    return Object.assign({
      emitted,
      settings: {},
      connectors: [],
      offerMirrorCalendar: true,
      errorMessage: null,
      $root: {$emit: (name, payload) => emitted.push({name, payload})},
      $t: key => key,
      $set: (target, key, value) => target[key] = value,
      $settingsService: {saveUserConnector: () => Promise.resolve()},
      $remoteEventConnector: {copyMeetingsEnabled: () => true},
      refreshConnectorsList: () => {},
      readMirrorCalendar: AgendaConnector.methods.readMirrorCalendar,
      offerMirrorCalendarUnlessPresent: AgendaConnector.methods.offerMirrorCalendarUnlessPresent,
    }, overrides || {});
  }

  /**
   * Whether the creation step was offered on that receiver.
   *
   * @param {Object} host the receiver the methods ran against
   * @returns {Boolean} true when the drawer was asked to open
   */
  function offered(host) {
    return host.emitted.some(event => event.name === OPEN_EVENT);
  }

  /**
   * Offers the step and waits for every branch of it to have run.
   *
   * <p><b>Deliberately not `await`ing what the method returns.</b> A pin that
   * did would be settled by the returned promise and by nothing else — and the
   * code this pins is a `catch` handler, which the very mutation the pin exists
   * to catch can leave running one microtask late while the method itself
   * resolves at once. Both pins passed against the defect on that timing until
   * this helper replaced them. Draining the queue instead settles the offer
   * whichever branch produced it, and whether or not the method hands a promise
   * back at all.</p>
   *
   * @param {Object} host the receiver to run against
   * @param {Object} connector the connector just connected
   * @returns {Promise} resolves once nothing is left pending
   */
  function offerAndSettle(host, connector) {
    host.offerMirrorCalendarUnlessPresent(connector);
    return new Promise(resolve => setTimeout(resolve, 0));
  }

  /**
   * A connector answering the destination directly, the contract the CalDAV
   * add-on implements.
   *
   * @param {Object} answer what `getMirrorCalendar` does: {resolves} or {rejects} or {throws}
   * @param {Object} extra anything else the descriptor should carry
   * @returns {Object} the connector descriptor
   */
  function connectorAnswering(answer, extra) {
    return Object.assign({
      name: 'agenda.caldavCalendar.6',
      canCreateCalendar: true,
      getMirrorCalendar: () => {
        if (answer.throws) {
          throw answer.throws;
        }
        return answer.rejects ? Promise.reject(answer.rejects) : Promise.resolve(answer.resolves);
      },
    }, extra || {});
  }

  describe('offerMirrorCalendarUnlessPresent', () => {

    it('offers the step when the account genuinely holds no destination', async () => {
      const host = receiver();

      await offerAndSettle(host, connectorAnswering({resolves: null}));

      expect(offered(host)).toBe(true);
    });

    /**
     * Reconnecting an account set up before leaves its calendar on the server;
     * asking again to create it reads as the connection having lost it.
     */
    it('stays quiet when the account already holds one', async () => {
      const host = receiver();

      await offerAndSettle(host, connectorAnswering({resolves: {id: '/dav/cal/u/exo-meetings/', name: 'eXo Meetings'}}));

      expect(offered(host)).toBe(false);
    });

    /**
     * The defect itself: a transient failure of the destination read is not an
     * account without a destination, and must not be turned into an offer to
     * create one.
     */
    it('offers nothing when the destination could not be read', async () => {
      const host = receiver();

      await offerAndSettle(host, connectorAnswering({rejects: new Error('500 from the server')}));

      expect(offered(host)).toBe(false);
    });

    /**
     * The same, for a connector that fails by throwing rather than by
     * rejecting — one failure, two shapes, and only one of them used to reach
     * the branch that swallowed it.
     *
     * <p>What the second shape cost is worth naming, because it is not the
     * offer: a synchronous throw escaped `offerMirrorCalendarUnlessPresent`
     * into `connect`'s own `catch`, which sets
     * `errorMessage = $t('agenda.connectionFailure')`. The account was
     * connected, and the screen said the connection had failed.</p>
     *
     * <p>No connector the platform ships takes this path — the CalDAV one
     * answers `getMirrorCalendar` with a `fetch` chain, which rejects rather
     * than throws. It is a guard on the contract the extension point
     * publishes, for a connector contributed by somebody else, and it is
     * pinned here because nothing else in the product would catch it.</p>
     */
    it('offers nothing when the connector throws instead of rejecting', async () => {
      const host = receiver();

      await offerAndSettle(host, connectorAnswering({throws: new Error('no endpoint')}));

      expect(offered(host)).toBe(false);
    });

    /**
     * The older contract — an id, then a scan of the listing — fails the same
     * way and is held to the same rule.
     */
    it('offers nothing when the older id-then-listing contract fails', async () => {
      const host = receiver();

      await offerAndSettle(host, {
        name: 'agenda.legacyCalendar',
        canCreateCalendar: true,
        getMirrorCalendarId: () => Promise.resolve('/dav/cal/u/exo-meetings/'),
        listCalendars: () => Promise.reject(new Error('unreachable')),
      });

      expect(offered(host)).toBe(false);
    });

  });

  describe('the gate at connect time', () => {

    /**
     * Connects through the component's own method, so the pin sits on the
     * condition guarding the offer and not on a paraphrase of it.
     *
     * @param {Object} connector the descriptor to connect
     * @param {Object} overrides anything the pin states on the receiver
     * @returns {Promise<Object>} the receiver, once connecting has settled
     */
    function connect(connector, overrides) {
      const host = receiver(Object.assign({offerMirrorCalendarUnlessPresent: jest.fn(() => Promise.resolve())}, overrides || {}));
      return AgendaConnector.methods.connect.call(host, connector).then(() => host);
    }

    /**
     * A descriptor of a server whose copies go to the account's own default
     * calendar: the CalDAV add-on stamps `canCreateCalendar` false on it, and
     * nothing here may offer to create anything. Reached before any read of
     * the destination — which is the point: the offer is refused on what the
     * registration says, not on what a server answered.
     */
    it('never offers the step for a connector that cannot create a calendar', async () => {
      const connector = connectorAnswering({resolves: null}, {canCreateCalendar: false, connect: () => Promise.resolve('john')});

      const host = await connect(connector);

      expect(host.offerMirrorCalendarUnlessPresent).not.toHaveBeenCalled();
      expect(offered(host)).toBe(false);
    });

    it('offers the step for a connector that can create one', async () => {
      const connector = connectorAnswering({resolves: null}, {connect: () => Promise.resolve('john')});

      const host = await connect(connector);

      expect(host.offerMirrorCalendarUnlessPresent).toHaveBeenCalledWith(connector);
    });

    /**
     * In the settings the same step is reached by turning the copy switch on,
     * so connecting from there does not take the page over with a drawer.
     */
    it('never offers the step where connecting is one preference among others', async () => {
      const connector = connectorAnswering({resolves: null}, {connect: () => Promise.resolve('john')});

      const host = await connect(connector, {offerMirrorCalendar: false});

      expect(host.offerMirrorCalendarUnlessPresent).not.toHaveBeenCalled();
    });

  });

});
