<!--
 Copyright (C) 2026 eXo Platform SAS.

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
-->
<template>
  <exo-drawer
    ref="mirrorCalendarDrawer"
    class="agendaMirrorCalendarDrawer"
    right
    @closed="announceOutcome">
    <template slot="title">
      {{ $t('agenda.mirrorCalendar.title') }}
    </template>
    <template slot="content">
      <div class="d-flex flex-column mx-4 mt-4">
        <div class="text-subtitle">
          {{ $t('agenda.mirrorCalendar.introduction', {0: companyName}) }}
        </div>
        <v-alert
          v-if="creationRefused"
          type="info"
          class="mt-4 my-auto"
          dense
          text>
          {{ creationNotice }}
        </v-alert>
        <!--
          Creating is the only offered choice. Handing the user a calendar
          picker instead would invite them to point the copies at the work
          calendar they already keep, without a way to foresee what that
          means — so the fallback is not a choice either: when the server
          refuses to create a calendar, the connector adopts the account's
          first calendar on its own and the notice above names it. Nothing
          shows twice in either case: the copies read back from the
          destination are recognised by the identifier stored at push time
          and filtered from the display.
        -->
        <div class="agenda-mirror-calendar-choice mt-4">
          <v-icon
            :color="calendarColor"
            size="16"
            class="me-2">
            fa-circle
          </v-icon>
          <span class="font-weight-bold">{{ $t('agenda.mirrorCalendar.createChoice', {0: calendarName}) }}</span>
        </div>
      </div>
    </template>
    <template slot="footer">
      <div class="d-flex">
        <v-spacer />
        <v-btn
          class="btn me-2"
          @click="close">
          {{ $t('agenda.button.cancel') }}
        </v-btn>
        <v-btn
          :disabled="!canApply"
          :loading="saving"
          class="btn btn-primary"
          @click="apply">
          {{ $t('agenda.button.apply') }}
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>

<script>
export default {
  data: () => ({
    connector: null,
    creationRefused: false,
    adoptedCalendarName: null,
    calendars: [],
    saving: false,
    applied: false,
    companyName: eXo.env.portal.companyName || '',
    calendarColor: '',
  }),
  computed: {
    /**
     * The name the mirror calendar will be created with: the platform
     * branding name plus "Meetings", composed through an i18n pattern so the
     * word order follows the language of the user at that moment. The name is
     * written once at creation and never renamed afterwards — the href is the
     * identity of the collection.
     *
     * @returns {String} the localised branded calendar name
     */
    calendarName() {
      return this.$t('agenda.mirrorCalendar.name', {0: this.companyName});
    },
    /**
     * Whether the apply button should be active. It is not once the server has
     * refused to create the calendar: there is nothing left to apply, and the
     * message explains where meetings will go instead.
     *
     * @returns {Boolean} true when the creation can still be applied
     */
    canApply() {
      return !this.creationRefused;
    },
    /**
     * What the refusal notice says. When an existing calendar was adopted as
     * the destination, the notice names it — the copies genuinely go there,
     * and "the first calendar of your account" tells a user nothing they can
     * recognise in their own client. The unnamed wording remains only for an
     * account holding no calendar at all, where there is nothing to adopt
     * and nowhere for the copies to go.
     *
     * @returns {String} the sentence shown in the info alert
     */
    creationNotice() {
      return this.adoptedCalendarName
        ? this.$t('agenda.mirrorCalendar.adopted', {0: this.adoptedCalendarName})
        : this.$t('agenda.mirrorCalendar.creationRefused');
    },
  },
  created() {
    this.$root.$on('agenda-connector-mirror-calendar-open', this.open);
  },
  beforeDestroy() {
    this.$root.$off('agenda-connector-mirror-calendar-open', this.open);
  },
  methods: {
    /**
     * Opens the drawer for a freshly connected connector, preselected on
     * creating the branded calendar — offered, not imposed: the user may
     * point the mirror at an existing calendar instead, or close the drawer.
     *
     * @param {Object} connector the connector that just connected
     * @returns {void}
     */
    open(connector) {
      if (!connector || !connector.canCreateCalendar) {
        return;
      }
      this.connector = connector;
      this.creationRefused = false;
      this.adoptedCalendarName = null;
      this.applied = false;
      this.retrieveBranding();
      if (this.$refs.mirrorCalendarDrawer) {
        this.$refs.mirrorCalendarDrawer.open();
      }
    },
    /**
     * Closes the drawer without touching the connector settings.
     *
     * @returns {void}
     */
    close() {
      if (this.$refs.mirrorCalendarDrawer) {
        this.$refs.mirrorCalendarDrawer.close();
      }
    },
    /**
     * Says how the step ended, once the drawer has actually closed. A caller
     * that turned something on to open it — the push switch in the user
     * settings — has to know that the user backed out, so it can put itself
     * back rather than claim a destination that was never chosen.
     * @returns {void}
     */
    announceOutcome() {
      if (!this.applied) {
        this.$root.$emit('agenda-connector-mirror-calendar-cancelled');
      }
    },
    /**
     * Reads the platform branding, for the name and the colour the created
     * calendar carries: a white-labelled deployment must produce
     * "Acme Meetings" in the brand colour, never a hardcoded product name.
     *
     * @returns {Promise} resolves once the branding is read
     */
    retrieveBranding() {
      return this.$brandingService.getBrandingInformation()
        .then(branding => {
          this.companyName = branding && branding.companyName || this.companyName;
          this.calendarColor = branding && branding.themeStyle && branding.themeStyle.primaryColor || '';
        })
        .catch(() => {
          // the synchronous eXo.env.portal.companyName default remains
        });
    },
    /**
     * Creates the branded calendar that will receive the meetings eXo pushes,
     * or — when the server refuses creating calendars — settles for the
     * existing calendar the connector adopted instead.
     *
     * @returns {Promise} resolves once the mirror destination is stored
     */
    apply() {
      this.saving = true;
      return this.connector.createCalendar({
        name: this.calendarName,
        color: this.calendarColor,
        description: this.$t('agenda.mirrorCalendar.description', {0: this.companyName}),
      })
        .then(result => {
          if (result && result.adopted) {
            this.destinationAdopted(result);
          } else {
            this.calendarCreated();
          }
        })
        .catch(error => this.handleFailure(error))
        .finally(() => this.saving = false);
    },
    /**
     * The branded calendar exists on the server: the step is complete, the
     * drawer closes and the success is announced.
     * @returns {void}
     */
    calendarCreated() {
      this.applied = true;
      this.close();
      this.$root.$emit('agenda-connector-mirror-calendar-done');
      this.$root.$emit('alert-message', this.$t('agenda.mirrorCalendar.saved', {0: this.calendarName}), 'success');
    },
    /**
     * The server refused to create a calendar and the connector adopted an
     * existing one of the account as the destination instead. A destination
     * now exists, so the step counts as done — the push switch may latch —
     * but the drawer stays open on the notice naming the adopted calendar
     * rather than closing over a success alert: the user asked for a new
     * calendar and must read that their meetings go to an existing one.
     *
     * @param {Object} result what createCalendar resolved with
     * @param {String} result.name display name of the adopted calendar
     * @returns {void}
     */
    destinationAdopted(result) {
      this.adoptedCalendarName = result.name;
      this.creationRefused = true;
      this.applied = true;
      this.$root.$emit('agenda-connector-mirror-calendar-done');
    },
    /**
     * Handles a failed apply. A server refusing to create a calendar — with
     * no calendar of the account left to adopt as the destination — is not
     * an eXo error: it is explained here, at connect time, rather than
     * failing silently at the first push. Any other failure is reported as
     * an error.
     *
     * @param {Object} error the failure raised by the connector
     * @returns {void}
     */
    handleFailure(error) {
      if (error && error.calendarCreationRefused) {
        this.creationRefused = true;
      } else {
        console.error('cannot configure the mirror calendar', error);
        this.$root.$emit('alert-message', this.$t('agenda.mirrorCalendar.saveError'), 'error');
      }
    },
  },
};
</script>
