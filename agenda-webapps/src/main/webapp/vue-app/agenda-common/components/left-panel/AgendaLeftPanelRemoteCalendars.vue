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
 * along with this program. If not, see <gnu.org/licenses>.
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

    Nothing is drawn while loading either — a section that appears, empties
    and vanishes is worse than one that arrives when it has content.
  -->
  <div v-if="!loading && groups.length" class="d-flex flex-column">
    <section
      v-for="group in groups"
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
                offset-y
                left>
                <template #activator="{ on, attrs }">
                  <v-btn
                    v-bind="attrs"
                    v-on="on"
                    :title="$t('agenda.calendar.actions')"
                    :aria-label="$t('agenda.calendar.actions')"
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
            -->
            <v-list-item-action
              v-if="hasKnownOwner(calendar) || calendar.readOnly === true"
              class="my-0 ms-2 flex-grow-0 justify-center">
              <exo-user-avatar
                v-if="hasKnownOwner(calendar)"
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
/**
 * Label key of the section that gathers the calendars shared with the user.
 * A section is keyed and titled by its name, the provider sections by the
 * provider's own label key, so this one is a label key too.
 */
const SHARED_WITH_ME_SECTION = 'agenda.leftPanel.sharedWithMe';

export default {
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
    loading: false,
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
     * @returns {void}
     */
    retrieveCalendars() {
      const connectors = this.connectedConnectors;
      if (!connectors.length) {
        this.setGroups([]);
        return;
      }
      this.loading = true;
      // Connecting fires two of these in quick succession — one the moment the
      // account is marked connected, one once its first synchronisation has
      // finished — and they answer different things. Landing out of order,
      // the first overwrites the second and the panel keeps describing the
      // account as it no longer stands, until the page is reloaded.
      //
      // The same guard the events grid uses, for the same reason: only the
      // newest request may write.
      const requestId = ++this.calendarsRequestId;
      Promise.all(connectors.map(connector =>
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
      }).finally(() => {
        if (requestId === this.calendarsRequestId) {
          this.loading = false;
        }
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
        && typeof calendar.ownerUsername === 'string'
        && calendar.ownerUsername.length > 0;
    },
    /**
     * What to say about who shared a calendar: "Shared by <owner>" when the
     * connector named the owner — the eXo full name of a colleague, or the
     * server's display name of a stranger — and "Shared with you" when the
     * connector names no owner at all. Which servers can name one is the
     * connector's business, not this panel's.
     *
     * @param {Object} calendar calendar as the connector described it
     * @returns {String} the sentence, in the user's language
     */
    sharedLabel(calendar) {
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
     * The sections without one calendar, a section emptied by it dropped
     * with it: an empty section is a question the user cannot act on, the
     * same rule buildGroups applies.
     *
     * @param {Array} groups sections as drawn
     * @param {String} calendarId identity of the calendar to leave out
     * @returns {Array} new sections, the given ones untouched
     */
    withoutCalendar(groups, calendarId) {
      return groups
        .map(group => ({...group, calendars: group.calendars.filter(calendar => calendar.id !== calendarId)}))
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
     * On failure the row comes back exactly as it was and the error is said.
     *
     * The sections are put back only if no retrieval has been started since
     * the click: one that has is answering for the accounts as they now
     * stand, hidden calendar included since hiding failed, and the snapshot
     * would overwrite a fresher answer with an older one. The retrieval
     * counter is read, never advanced, for the same reason retrieveCalendars
     * owns it: advancing it would make an in-flight retrieval unable to
     * clear the loading flag, and the whole component hides while loading.
     *
     * The connector is called inside a promise executor: the call goes out
     * at once, and a connector throwing synchronously is handled as a
     * rejection, on the same path as a refused request, rather than escaping
     * the click handler with the row already gone.
     *
     * @param {Object} calendar calendar as buildGroups stamped it
     * @returns {Promise} resolves once the outcome has been shown, never
     *          rejects
     */
    hideCalendar(calendar) {
      const connector = this.connectorOf(calendar);
      if (!connector || typeof connector.hideCalendar !== 'function') {
        return Promise.resolve();
      }
      const groups = this.groups;
      const requestId = this.calendarsRequestId;
      this.setGroups(this.withoutCalendar(groups, calendar.id));
      return new Promise(resolve => resolve(connector.hideCalendar(calendar.id)))
        .then(() => {
          this.forgetVisibility(calendar);
          this.$root.$emit('alert-message', this.$t('agenda.leftPanel.sharedCalendarHidden', {0: calendar.name}), 'success');
          this.$root.$emit('agenda-refresh');
        })
        .catch(error => {
          console.error(`cannot hide the calendar ${calendar.name}`, error);
          if (requestId === this.calendarsRequestId) {
            this.setGroups(groups);
          }
          this.$root.$emit('alert-message', this.$t('agenda.leftPanel.hideSharedCalendarError'), 'error');
        });
    },
  },
};
</script>
