/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see gnu.org/licenses.
 */
<template>
  <!--
    One section per connected provider, titled with the provider's own label,
    so a calendar's header says where it comes from — the generic "Remote"
    header said only that it comes from somewhere else. The calendars a
    CalDAV server serves because someone else shared them with the user are
    the exception: they sit in one section titled "Shared with me", whichever
    server they come from, because what the user needs to know about such a
    calendar is not which server holds it but that it is someone else's — see
    buildGroups below.

    Sections are drawn here rather than by the panel, and only once there is
    something in them: this component is the only thing that knows which
    accounts answered with calendars, and an empty section titled with a
    provider name is a question the user cannot act on.

    It owns its sections, headers included, because the panel cannot hide
    one from the outside: v-show writes an inline display:none that Vuetify's
    d-flex utility overrides with !important, so a section stayed on screen
    however the count came out. Owning its own root is what makes the decision
    actually take effect.

    The sections stay on screen while the connectors are asked again, and
    are replaced only once the new answer is in (EXO-90355). The root used to
    hide itself for the whole of every retrieval, on the theory that a
    section which appears, empties and vanishes is worse than one that
    arrives with content — true of the first load, where there is nothing to
    show yet and visibleGroups is empty anyway, and exactly the flicker on
    every later one: agenda-refresh fires on each event saved, answered or
    pushed, and the connectors prop is rebuilt on every
    agenda-connectors-refresh, so "Shared with me" unmounted and remounted,
    rows and headers, on nearly every action in the agenda. Keyed by the
    calendar's id, a row is patched in place when the answer replaces it,
    and a share that went away leaves with the answer that no longer lists
    it.
  -->
  <div v-if="visibleGroups.length" class="d-flex flex-column">
    <section
      v-for="group in visibleGroups"
      :key="group.name"
      class="agenda-left-panel-section d-flex flex-column mb-5">
      <div class="agenda-left-panel-title text-sub-title">
        <span class="flex-grow-1">{{ $t(group.name) }}</span>
      </div>
      <!-- The panel's indent lives on this wrapper, not on the list: Vuetify's
           pa-0 is !important and would wipe it off the list itself. -->
      <div class="agenda-left-panel-calendars">
        <v-list
          class="pa-0"
          dense>
          <v-list-item
            v-for="calendar in group.calendars"
            :key="calendar.id"
            class="agenda-calendar-settings px-0">
            <!--
              The hover is the name, which the row may have cut short, and on
              a shared calendar who shared it: the header says it is someone
              else's, the row says whose — the avatar beside it does too, but
              only for an owner this deployment knows as a user.
            -->
            <v-list-item-content :title="rowTitle(calendar)" class="flex-grow-1 pa-0">
              <v-checkbox
                :input-value="isDisplayed(calendar)"
                :color="calendar.color"
                :label="calendar.name"
                class="agenda-calendar-settings-color ms-4"
                dense
                hide-details
                @change="toggle(calendar)" />
            </v-list-item-content>
            <!--
              Hiding a calendar someone shared with the user, for good: the
              checkbox only hides its events until the page is reloaded, and a
              share the user never asked to see came back on every visit. The
              connector records the choice on the server, and the agenda user
              settings list what was hidden and show it again — the snackbar
              says where.

              The same three-dots menu the personal rows carry, one entry in
              it, revealed on hover and on keyboard focus by the same class,
              so the two lists read alike and the panel stays a list of
              calendars rather than a column of controls. Offered only on a
              row buildGroups stamped as shared, and only when the connector
              that listed it can hide it: every other provider, and a CalDAV
              add-on older than this contract, declares no hideCalendar, gets
              no menu, and the row reads exactly as it did.

              Placed before the owner marker, where the personal rows put
              their menu last: the avatar and the lock are static markers
              aligned on the 24px column the section headers' icons share
              (see the marker's own comment), and a menu after them would
              push them out of that column on every shared row, hovered or
              not — the hover reveals the menu by opacity and does not take
              its width back. So the marker keeps the column and the menu
              appears at its left. Keyboard users reach it by tabbing:
              focus-within is what reveals it, and the button carries its
              own label.
            -->
            <v-list-item-action
              v-if="canHide(calendar)"
              class="my-0 ms-2 flex-grow-0 agenda-calendar-actions">
              <v-menu
                :value="isRowMenuOpen(`${group.name}:${calendar.id}`)"
                content-class="agendaCalendarRowMenu"
                offset-y
                left
                @input="toggleRowMenu(`${group.name}:${calendar.id}`, $event)">
                <template #activator="{ on, attrs }">
                  <v-btn
                    v-bind="attrs"
                    v-on="on"
                    :title="$t('agenda.calendar.actions')"
                    :aria-label="actionsLabel(calendar)"
                    icon
                    x-small>
                    <v-icon size="14">fa-ellipsis-v</v-icon>
                  </v-btn>
                </template>
                <v-list dense class="pa-0">
                  <v-list-item @click="hideCalendar(calendar)">
                    <v-list-item-title>{{ $t('agenda.leftPanel.hideSharedCalendar') }}</v-list-item-title>
                  </v-list-item>
                </v-list>
              </v-menu>
            </v-list-item-action>
            <!--
              A calendar the account may only read — one its owner shared with
              the user, typically — says so, because it sits next to calendars
              the user owns and looks exactly like them. The rows here carry
              no edit or delete action for any calendar, so read-only is not a
              menu with fewer entries but a marker, static and always visible.
              The flag is the connector's word, `readOnly` on the calendar it
              listed, whichever provider listed it: CalDAV derives it from the
              collection's privileges, Google from the calendar's access role
              (reader and freeBusyReader only — writer and owner can write).
              A connector that lists no such flag draws no marker.

              When the calendar is one buildGroups stamped as shared with the
              user and its owner is a user of this deployment — a colleague's
              eXo calendar shared on the server, which the CalDAV connector
              names by `ownerUsername` — the owner's avatar takes the
              lock's place: it says who the calendar belongs to, which a lock
              does not, and the read-only part is what "Shared with me" already
              says. The shared component is used in its picture-only mode with
              its popover and profile link, so the owner is one hover or one
              click away, as anywhere else on the platform. An owner the
              connector names only by a display name — a share made on the
              server by someone who is not a user here — has no profile to
              link, so that row keeps the lock and names the owner in the
              hover.

              The icon alone would not be announced: Vuetify hides a v-icon
              that has no click listener from assistive technology, so the
              wrapper is what carries the role and the label.

              Centred in the action slot so that it lines up with the section
              headers' icons: Vuetify gives the slot a 24px minimum width and
              lays its content out from the start, while a header icon is a
              14px glyph centred in a 24px button — left as it was, the lock
              ended five pixels short of the column the plug, plus and filter
              icons share. Same slot the personal rows use for their menu. The
              avatar's own side margins are taken back in the panel's
              stylesheet for the same reason (agenda.less, .user-wrapper).

              A resource the user subscribed to — a room, a pool vehicle —
              is nobody's face: the connector says so with `ownerKind`, and
              the row draws a neutral resource glyph in the avatar's place,
              labelled "Resource: <name>" (EXO-90275). It is still a shared,
              read-only calendar, with the same Hide menu and nothing else.
            -->
            <v-list-item-action
              v-if="isResource(calendar) || hasKnownOwner(calendar) || calendar.readOnly === true"
              class="my-0 ms-2 flex-grow-0 justify-center">
              <span
                v-if="isResource(calendar)"
                :title="resourceLabel(calendar)"
                :aria-label="resourceLabel(calendar)"
                role="img"
                class="d-flex agenda-remote-calendar-resource">
                <v-icon size="14" class="text-light-color">
                  fas fa-cube
                </v-icon>
              </span>
              <exo-user-avatar
                v-else-if="hasKnownOwner(calendar)"
                :profile-id="calendar.ownerUsername"
                :name="calendar.ownerDisplayName"
                :aria-label="sharedLabel(calendar)"
                :size="20"
                avatar
                popover />
              <span
                v-else
                :title="$t('agenda.leftPanel.readOnlyCalendar')"
                :aria-label="$t('agenda.leftPanel.readOnlyCalendar')"
                role="img"
                class="d-flex">
                <v-icon size="14" class="text-light-color">
                  fas fa-lock
                </v-icon>
              </span>
            </v-list-item-action>
          </v-list-item>
        </v-list>
      </div>
    </section>
  </div>
</template>

<script>
import calendarRowMenuMixin from '../../js/CalendarRowMenuMixin.js';

/**
 * Label key of the section that gathers the calendars shared with the user.
 * A section is keyed and titled by its name, the provider sections by the
 * provider's own label key, so this one is a label key too.
 */
const SHARED_WITH_ME_SECTION = 'agenda.leftPanel.sharedWithMe';

export default {
  mixins: [calendarRowMenuMixin],
  props: {
    connectors: {
      type: Array,
      default: () => [],
    },
  },
  data: () => ({
    calendarsRequestId: 0,
    groups: [],
    hiddenIds: [],
    hidingIds: [],
    // The retrieval scheduled but not yet sent, its timer and the settling of
    // its promise — see retrieveCalendars.
    retrieval: null,
    retrievalTimer: 0,
    retrievalResolve: null,
  }),
  computed: {
    /**
     * The connectors that get a section: connected and able to list calendars,
     * CalDAV included.
     *
     * CalDAV used to be excluded here by identity, on the assumption that
     * every one of its collections is materialised as one of the user's own
     * personal calendars and so already sits under My Calendars — a section
     * of its own would have shown each calendar twice. That assumption broke
     * the day a calendar shared with the user stayed unmaterialised: its
     * events were drawn on the grid as remote events, and no calendar
     * anywhere named them, coloured them or let the user hide them. Once
     * EXO-90235 lands, a shared calendar stays unmaterialised for good and is
     * served read-only, so the exclusion would have hidden it for good.
     *
     * What keeps a materialised calendar from appearing twice is the
     * connector, not this panel. Its listing serves only the collections eXo
     * holds no calendar for — the materialised ones, the mirror and the ones
     * an eXo created are left out on the server — so a CalDAV section lists
     * exactly what is read live and read-only, and a user whose collections
     * are all materialised gets no CalDAV section at all. `canListCalendars`
     * is what chooses, and only Google and CalDAV declare it — Office 365 and
     * Exchange predate the contract and are never asked. `canPush` is
     * deliberately not consulted: it is dynamic on Google, and a section must
     * not vanish mid-session when the user grants the write scope.
     *
     * `connected` is the runtime state and is what decides. `isSignedIn` is a
     * static property on the CalDAV descriptor, always true whether or not an
     * account is configured, so gating on it alone meant the section kept
     * asking after the user had disconnected — and the request went out with a
     * null username, drawing the browser's own credentials prompt over the
     * agenda.
     *
     * @returns {Array} the connectors worth asking
     */
    connectedConnectors() {
      return (this.connectors || []).filter(connector => connector
          && connector.canListCalendars
          && connector.connected
          && typeof connector.listCalendars === 'function');
    },
    /**
     * Whether an account is in the middle of connecting.
     *
     * An account is marked connected before its first synchronisation runs,
     * and only that synchronisation takes its collections in. Asking in
     * between gets a true answer to the wrong question: nothing has
     * materialised yet, so every collection on the account still counts as
     * one eXo is not holding, and the section shows the lot for the second it
     * takes the synchronisation to finish.
     *
     * @returns {Boolean} true while a connector is still connecting
     */
    connectorsConnecting() {
      return (this.connectors || []).some(connector => connector && connector.loading);
    },
    /**
     * The sections as drawn: the connectors' answer without the calendars a
     * hide is in flight for (see hideCalendar), a section emptied by that
     * dropped with them — an empty section is a question the user cannot
     * act on, the same rule buildGroups applies.
     *
     * Derived rather than written back into `groups`, so that the listing
     * and the user's pending actions never overwrite each other: a
     * retrieval landing mid-hide still lists the calendar, and it stays out
     * of sight all the same; a hide that fails puts its calendar back by
     * forgetting the id, whatever else happened to the listing meanwhile.
     *
     * @returns {Array} the sections to draw, none of them empty
     */
    visibleGroups() {
      return this.withoutCalendars(this.groups, this.hidingIds);
    },
  },
  watch: {
    /**
     * Reacts to an account being connected or disconnected while the agenda is
     * open, so the sections fill or empty without a reload.
     * @returns {void}
     */
    connectedConnectors() {
      // Not mid-connect: the account is flagged connected before its first
      // synchronisation, and answering then paints collections that are about
      // to stop being remote. The connecting watcher below asks once it is
      // over, so nothing is lost by staying quiet here.
      if (this.connectorsConnecting) {
        return;
      }
      this.retrieveCalendars();
    },
    /**
     * Asks once connecting is over.
     *
     * Its own trigger rather than a reliance on the refresh the connect flow
     * emits: this list must fill even if that signal is not sent, and asking
     * twice costs one request while not asking at all leaves the section
     * empty for the life of the page.
     *
     * @param {Boolean} connecting whether an account is still connecting
     * @returns {void}
     */
    connectorsConnecting(connecting) {
      if (!connecting) {
        this.retrieveCalendars();
      }
    },
  },
  created() {
    this.retrieveCalendars();
    // The same signal the personal list listens to, because materialising a
    // collection changes both panels at once: it leaves this one and joins
    // that one. Listening on only one side is what let a calendar sit under
    // Remote while already being shown under Personal.
    this.$root.$on('agenda-refresh-personal-calendars', this.retrieveCalendars);
    // And on the agenda's general refresh, which is what actually covers the
    // case this list kept getting wrong. Everything else that refreshes it —
    // creation, the connected-accounts watcher, connecting, disconnecting —
    // happens around the account changing. None of them fires when a
    // synchronisation materialises a collection, and that is the moment a
    // calendar stops being remote.
    //
    // So a page that loaded while a collection was still unbound kept
    // offering it for the life of that page, however many times it was
    // synchronised afterwards, and a reload only reproduced the same stale
    // answer if it happened in the same window. agenda-refresh is emitted
    // after a synchronisation completes, among a dozen other places, so this
    // list now corrects itself on the signal that matters rather than only on
    // the ones that happen to be near it.
    this.$root.$on('agenda-refresh', this.retrieveCalendars);
  },
  beforeDestroy() {
    this.$root.$off('agenda-refresh-personal-calendars', this.retrieveCalendars);
    this.$root.$off('agenda-refresh', this.retrieveCalendars);
    window.clearTimeout(this.retrievalTimer);
    // The cancelled retrieval still settles, as retrieveCalendars promises
    if (this.retrievalResolve) {
      this.retrievalResolve();
      this.retrievalResolve = null;
    }
  },
  methods: {
    /**
     * Records the provider sections to draw.
     *
     * @param {Array} groups sections to show, each carrying its label key —
     *          the provider's, or the shared section's — and its calendars
     * @returns {void}
     */
    setGroups(groups) {
      this.groups = groups;
    },
    /**
     * Asks every sectioned connector for the calendars of the account behind
     * it, and renders one titled section per provider that answered with any,
     * plus the "Shared with me" section when a CalDAV server listed calendars
     * shared with the user (see buildGroups).
     *
     * A connector that does not declare canListCalendars is skipped rather
     * than called, so the ones that predate this contract — Office 365 and
     * Outlook Exchange — are untouched. A connector that fails is logged and
     * dropped: one unreachable account must not empty the other providers'
     * sections.
     *
     * One user action reaches here several times over (EXO-90355): a drawer
     * emits agenda-refresh-personal-calendars and agenda-refresh back to
     * back, a share drawer's agenda-connectors-refresh has the connectors
     * prop rebuilt — a new array every time — which fires the
     * connectedConnectors watcher, and connecting fires the two watchers in
     * the same flush. Each call used to send its own request, and with the
     * sections hidden for the whole of each one the panel emptied and refilled
     * as many times. So the calls of one turn are gathered into one request:
     * the first schedules it for right after the current task and its
     * microtasks — where the watchers run — and the next ones join it. The
     * gap is one timer tick, so a share that appears or goes still shows on
     * the very signal that reports it.
     *
     * @returns {Promise} resolves once the gathered request has answered and
     *          the sections are drawn, or at once when the panel is destroyed
     *          before the request is sent; never rejects
     */
    retrieveCalendars() {
      if (!this.retrieval) {
        this.retrieval = new Promise(resolve => {
          this.retrievalResolve = resolve;
          this.retrievalTimer = window.setTimeout(() => {
            this.retrieval = null;
            this.retrievalResolve = null;
            resolve(this.askConnectors());
          });
        });
      }
      return this.retrieval;
    },
    /**
     * Sends the request retrieveCalendars gathered: asks every sectioned
     * connector now and replaces the sections with what they answer.
     *
     * The sections on screen stay as they are until the answer is in: a
     * retrieval is most often a re-read after an action elsewhere, and a list
     * that blanks while it re-reads is the flicker of EXO-90355.
     *
     * @returns {Promise} resolves once the sections are drawn; never rejects
     */
    askConnectors() {
      const connectors = this.connectedConnectors;
      // Connecting fires two of these in quick succession — one the moment the
      // account is marked connected, one once its first synchronisation has
      // finished — and they answer different things. Landing out of order,
      // the first overwrites the second and the panel keeps describing the
      // account as it no longer stands, until the page is reloaded.
      //
      // The same guard the events grid uses, for the same reason: only the
      // newest request may write. Taken before the early return too, so a
      // disconnect landing while a listing is in flight is not overwritten by
      // that listing's answer.
      const requestId = ++this.calendarsRequestId;
      if (!connectors.length) {
        this.setGroups([]);
        return Promise.resolve();
      }
      return Promise.all(connectors.map(connector =>
        connector.listCalendars()
          // The calendar eXo writes its copies to is left out of the list: it
          // holds nothing but duplicates of events the agenda already shows,
          // so displaying it would double every meeting on the grid.
          .then(calendars => this.$remoteEventConnector.excludeMirrorCalendar(connector, calendars))
          .then(calendars => ({connector, calendars: calendars || []}))
          .catch(error => {
            console.error(`cannot list the calendars of ${connector.name}`, error);
            return {connector, calendars: []};
          })
      )).then(answers => {
        if (requestId !== this.calendarsRequestId) {
          // A newer retrieval was started since: its answer is the one that
          // reflects the accounts as they now stand.
          return;
        }
        this.setGroups(this.buildGroups(answers));
      });
    },
    /**
     * Turns the connectors' answers into the sections to draw.
     *
     * One section per provider, in the connectors' order, holding what the
     * account itself owns; then one "Shared with me" section holding, across
     * every CalDAV server, the calendars someone else shared with the user.
     * A provider that answered with nothing, or with shared calendars only,
     * gets no section of its own: an empty section is a question the user
     * cannot act on.
     *
     * The shared calendars are gathered rather than left under their server
     * because the server's name answers the wrong question. Under "Bluemind",
     * a colleague's calendar looked like one more calendar of the user's own
     * account — the lock said it could not be written to, nothing said it was
     * someone else's. Gathered, the header says what they have in common and
     * the row can say whose each one is. The section comes last, after the
     * providers' own: the panel goes from what is mine outwards — my own
     * calendars, then my accounts, then what others let me see — and a
     * section merged across servers has no one provider to sit next to. It
     * also lands right above Spaces, the other list of calendars the user
     * does not own.
     *
     * What counts as shared is the CalDAV connector's word, `shared` on the
     * calendar it listed, and is distinct from `readOnly`: an own calendar the
     * synchronisation has not taken in yet is read-only here for a moment,
     * and is still the user's own, so it keeps its server's section. A
     * connector that lists no `shared` flag at all — an older CalDAV add-on —
     * answers exactly as before: every calendar under its server's name. The
     * other providers are not asked: Google lists no such flag, and its
     * calendars stay where they were, lock included.
     *
     * The verdict is taken once, here, and stamped on the row as
     * `sharedWithMe`: the section, the hover and the owner marker all read
     * the stamp rather than each re-reading the connector's fields, so a row
     * cannot land in a server's section and still be labelled as a share —
     * which is what happened when the hover read `shared` off every
     * provider and the marker read `ownerUsername` off every row. The
     * connector's own object is left as it answered it; the stamp goes on a
     * copy.
     *
     * A shared row is also stamped with the name of the connector that listed
     * it, `connectorName`: the section it lands in is not its server's, so
     * nothing else on the row says which connector to ask when the user acts
     * on it (see hideCalendar). The name and not the connector itself, so the
     * row stays plain data — the descriptor is looked up again among the
     * connected connectors at the moment of acting, which is also what keeps
     * a menu from acting on an account disconnected since the row was drawn.
     *
     * @param {Array} answers one entry per connector asked, `{connector,
     *          calendars}`, in the connectors' order
     * @returns {Array} the sections, each `{name, calendars}` with a label key
     *          for a name, none of them empty
     */
    buildGroups(answers) {
      const sharedCalendars = [];
      const groups = [];
      answers.forEach(({connector, calendars}) => {
        const own = [];
        calendars.forEach(calendar => {
          if (this.isSharedCalendar(connector, calendar)) {
            sharedCalendars.push({...calendar, sharedWithMe: true, connectorName: connector.name});
          } else {
            own.push(calendar);
          }
        });
        if (own.length) {
          groups.push({name: connector.name, calendars: own});
        }
      });
      if (sharedCalendars.length) {
        groups.push({name: SHARED_WITH_ME_SECTION, calendars: sharedCalendars});
      }
      return groups;
    },
    /**
     * Whether a calendar belongs to someone else and was shared with the user.
     *
     * Asked only of a CalDAV connector, by the `isCaldav` constant its
     * descriptor declares: `shared` is that connector's contract, and reading
     * it off another provider's calendars would move them on a word that
     * provider never meant. Strictly true, so an entry without the flag —
     * an older add-on — is the user's own, as it always read.
     *
     * @param {Object} connector the connector that listed the calendar
     * @param {Object} calendar calendar as the connector described it
     * @returns {Boolean} true when it is a calendar shared with the user
     */
    isSharedCalendar(connector, calendar) {
      return !!connector && connector.isCaldav === true && !!calendar && calendar.shared === true;
    },
    /**
     * Whether the calendar is a share whose owner is a user of this
     * deployment, one the avatar can show and link to.
     *
     * Asked of a row that buildGroups stamped as shared with the user, never
     * of the owner fields alone: a calendar that is the user's own has an
     * owner who is a user of this deployment too — the viewer — and were the
     * connector ever to name them, the row would otherwise show the viewer
     * their own face labelled as a share. Among the owner fields the username
     * is what decides, not the identity id: the avatar component resolves the
     * profile, the picture and the popover from the username, and would draw
     * a nameless placeholder from an id alone. The connector sends both
     * together or neither.
     *
     * @param {Object} calendar calendar as buildGroups stamped it
     * @returns {Boolean} true when the owner can be shown as a user
     */
    hasKnownOwner(calendar) {
      return !!calendar
        && calendar.sharedWithMe === true
        && !this.isResource(calendar)
        && typeof calendar.ownerUsername === 'string'
        && calendar.ownerUsername.length > 0;
    },
    /**
     * Whether the calendar is a resource's the user subscribed to — a room,
     * a pool vehicle — rather than a person's (EXO-90275).
     *
     * Asked of a row buildGroups stamped as shared, on the connector's word:
     * `ownerKind` is `RESOURCE` only for such a calendar. A connector that
     * sends no kind, or an older CalDAV add-on, draws every share as a
     * person's, as before.
     *
     * @param {Object} calendar calendar as buildGroups stamped it
     * @returns {Boolean} true when the owner is a resource
     */
    isResource(calendar) {
      return !!calendar && calendar.sharedWithMe === true && calendar.ownerKind === 'RESOURCE';
    },
    /**
     * What to say about a resource's calendar: "Resource: <name>", named as
     * the connector named the resource, else by the calendar's own name.
     *
     * @param {Object} calendar calendar as buildGroups stamped it
     * @returns {String} the sentence, in the user's language
     */
    resourceLabel(calendar) {
      return this.$t('agenda.leftPanel.resourceCalendar', {0: calendar.ownerDisplayName || calendar.name});
    },
    /**
     * What to say about who shared a calendar: "Shared by <owner>" when the
     * connector named the owner — the eXo full name of a colleague, or the
     * server's display name of a stranger — and "Shared with you" when the
     * connector names no owner at all. Which servers can name one is the
     * connector's business, not this panel's. A resource's calendar is said
     * to be one instead: "Resource: <name>".
     *
     * @param {Object} calendar calendar as the connector described it
     * @returns {String} the sentence, in the user's language
     */
    sharedLabel(calendar) {
      if (this.isResource(calendar)) {
        return this.resourceLabel(calendar);
      }
      return calendar.ownerDisplayName
        ? this.$t('agenda.leftPanel.sharedBy', {0: calendar.ownerDisplayName})
        : this.$t('agenda.leftPanel.sharedCalendar');
    },
    /**
     * The row's hover: the calendar's full name, and on a calendar shared
     * with the user who shared it. Judged on the stamp buildGroups set, the
     * same verdict that placed the row under "Shared with me", so the hover
     * and the header never disagree; a calendar that is not shared — or one
     * a provider other than CalDAV flagged, whose word that is not — keeps
     * the bare name it always had.
     *
     * @param {Object} calendar calendar as buildGroups stamped it
     * @returns {String} the hover text
     */
    rowTitle(calendar) {
      if (calendar.sharedWithMe !== true) {
        return calendar.name;
      }
      return `${calendar.name} — ${this.sharedLabel(calendar)}`;
    },
    /**
     * Whether a calendar's events are currently shown. Calendars are displayed
     * unless deliberately hidden, so a newly appearing one shows by default
     * rather than staying invisible until noticed.
     *
     * @param {Object} calendar calendar as the connector described it
     * @returns {Boolean} true when its events are shown
     */
    isDisplayed(calendar) {
      return !this.hiddenIds.includes(calendar.id);
    },
    /**
     * Shows or hides one calendar and publishes the resulting hidden set. The
     * set is one flat list across every provider: the grid filters events by
     * calendar id alone, whichever account the calendar lives on.
     *
     * @param {Object} calendar calendar the user just toggled
     * @returns {void}
     */
    toggle(calendar) {
      this.hiddenIds = this.isDisplayed(calendar)
        ? this.hiddenIds.concat(calendar.id)
        : this.hiddenIds.filter(id => id !== calendar.id);
      this.$root.$emit('agenda-remote-calendars-changed', this.hiddenIds.slice());
    },
    /**
     * The connector that listed a shared row, found again by the name
     * buildGroups stamped on it, among the connectors currently connected.
     *
     * @param {Object} calendar calendar as buildGroups stamped it
     * @returns {Object} the connector, or null when none connected bears the
     *          name — the account was disconnected since the row was drawn
     */
    connectorOf(calendar) {
      const name = calendar && calendar.connectorName;
      return name && this.connectedConnectors.find(connector => connector.name === name) || null;
    },
    /**
     * Whether the row gets the menu that hides the calendar for good.
     *
     * Only a row buildGroups stamped as shared with the user — hiding is what
     * one does with someone else's calendar; an own calendar is managed from
     * its account — and only when the connector that listed it declares
     * `hideCalendar`. The declaration is the contract, as `listCalendars` and
     * `calendarProblems` are for the other lists: a provider without it, or
     * a CalDAV add-on older than the method, gets no menu rather than a menu
     * that fails.
     *
     * @param {Object} calendar calendar as buildGroups stamped it
     * @returns {Boolean} true when the calendar can be hidden from here
     */
    canHide(calendar) {
      if (!calendar || calendar.sharedWithMe !== true) {
        return false;
      }
      const connector = this.connectorOf(calendar);
      return !!connector && typeof connector.hideCalendar === 'function';
    },
    /**
     * What a screen reader announces for a row's menu button: the actions of
     * that calendar, by name.
     *
     * The visible hover keeps the generic "Calendar actions" the personal
     * rows show, since a sighted user sees which row the button sits on. A
     * user tabbing through the list hears only the button, and heard the same
     * "Calendar actions" on every shared row, with nothing saying which
     * calendar the menu would hide.
     *
     * @param {Object} calendar calendar as buildGroups stamped it
     * @returns {String} the accessible name, in the user's language
     */
    actionsLabel(calendar) {
      return this.$t('agenda.leftPanel.sharedCalendarActions', {0: calendar.name});
    },
    /**
     * The sections without the given calendars, a section emptied by that
     * dropped with them.
     *
     * @param {Array} groups sections as the connectors answered them
     * @param {Array} calendarIds identities of the calendars to leave out
     * @returns {Array} new sections, the given ones untouched; the given
     *          sections themselves when there is nothing to leave out
     */
    withoutCalendars(groups, calendarIds) {
      if (!calendarIds.length) {
        return groups;
      }
      return groups
        .map(group => ({...group, calendars: group.calendars.filter(calendar => !calendarIds.includes(calendar.id))}))
        .filter(group => group.calendars.length);
    },
    /**
     * Drops a calendar from the locally hidden set, once it is hidden for
     * good, and tells the grid.
     *
     * The checkbox state would otherwise outlive the row: shown again from
     * the settings in the same session, the calendar would come back
     * unticked, its events filtered out by a choice the user made before
     * hiding it and cannot see any more.
     *
     * @param {Object} calendar the calendar just hidden
     * @returns {void}
     */
    forgetVisibility(calendar) {
      if (this.isDisplayed(calendar)) {
        return;
      }
      this.hiddenIds = this.hiddenIds.filter(id => id !== calendar.id);
      this.$root.$emit('agenda-remote-calendars-changed', this.hiddenIds.slice());
    },
    /**
     * Hides a shared calendar for good, through the connector that listed it.
     *
     * The row goes at once and the server is asked afterwards: the user
     * clicked to make it go, and a row that lingers until a round trip
     * completes looks like a click that did nothing. On success the snackbar
     * names the calendar and says where it can be shown again — the settings
     * are not where the user is — and the agenda's general refresh is
     * emitted: the grid re-reads the remote events, which the connector no
     * longer serves for a hidden collection, and this list re-asks the
     * connectors, which confirms the row gone from the server's own answer.
     * On failure the row comes back exactly where it was and the error is
     * said.
     *
     * The row is taken out by its id, on the in-flight list visibleGroups
     * reads, never by rewriting the sections: two hides in flight each own
     * their id, so one failing puts back its own calendar and only that one,
     * and a retrieval landing meanwhile — one started before the click, or
     * by the refresh of another hide's success — neither resurrects the row
     * nor gets overwritten by a snapshot older than it. On success the
     * calendar is also dropped from the sections themselves before its id is
     * released: the sections stay on screen while the refresh emitted just
     * after re-asks the connectors, so this is what keeps the row from
     * reappearing between the release of its id and the answer — and the
     * sections never list a calendar the server has hidden, should that
     * refresh ever go unheard.
     *
     * The connector is called inside a promise executor: the call goes out
     * at once, and a connector throwing synchronously is handled as a
     * rejection, on the same path as a refused request, rather than escaping
     * the click handler with the row already gone.
     *
     * The menu's button leaves with the row, so the keyboard focus falls
     * back to the document after the action — as it does after the personal
     * rows' delete.
     *
     * @param {Object} calendar calendar as buildGroups stamped it
     * @returns {Promise} resolves once the outcome has been shown, never
     *          rejects
     */
    hideCalendar(calendar) {
      const connector = this.connectorOf(calendar);
      if (!connector || typeof connector.hideCalendar !== 'function' || this.hidingIds.includes(calendar.id)) {
        return Promise.resolve();
      }
      this.hidingIds = this.hidingIds.concat(calendar.id);
      return new Promise(resolve => resolve(connector.hideCalendar(calendar.id)))
        .then(() => {
          this.setGroups(this.withoutCalendars(this.groups, [calendar.id]));
          this.forgetVisibility(calendar);
          this.$root.$emit('alert-message', this.$t('agenda.leftPanel.sharedCalendarHidden', {0: calendar.name}), 'success');
          this.$root.$emit('agenda-refresh');
        })
        .catch(error => {
          console.error(`cannot hide the calendar ${calendar.name}`, error);
          this.$root.$emit('alert-message', this.$t('agenda.leftPanel.hideSharedCalendarError'), 'error');
        })
        .finally(() => {
          this.hidingIds = this.hidingIds.filter(id => id !== calendar.id);
        });
    },
  },
};
</script>
