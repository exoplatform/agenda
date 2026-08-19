<template>
  <exo-drawer
    ref="calendarFilters"
    right
    @opened="onOpened"
    @closed="cancel">
    <template slot="title">
      {{ $t('agenda.filterAgendaTitle') }}
    </template>
    <template slot="content">
      <div class="d-flex flex-column mx-4 mt-4">
        <div class="font-weight-bold">{{ $t('agenda.filter.label.displayedEvents') }}</div>
        <div class="radio-group-container mt-n2 ms-n1">
          <v-radio-group
            v-model="eventType">
            <v-radio
              :label="$t('agenda.myEvent')"
              value="myEvents" />
            <v-radio
              :label="$t('agenda.declinedEvent')"
              value="declinedEvent" />
            <v-radio
              :label="$t('agenda.allEvents')"
              value="allEvents" />
          </v-radio-group>
        </div>
      </div>
      <div v-if="displayCalendarSelection" class="d-flex flex-column mx-4 my-1">
        <div class="d-flex align-center">
          <span class="font-weight-bold flex-grow-1">{{ $t('agenda.leftPanel.personalCalendars') }}</span>
          <!-- Same affordance as the left panel section header: creation goes
               through the calendar drawer, opening on top of this one -->
          <v-btn
            :title="$t('agenda.calendar.addCalendar')"
            icon
            max-width="24"
            max-height="24"
            class="flex-grow-0"
            @click="openPersonalCalendarDrawer">
            <v-icon size="14" class="text-light-color">
              fas fa-plus
            </v-icon>
          </v-btn>
        </div>
        <agenda-personal-calendar-list />
      </div>
      <div v-if="displayCalendarSelection" class="d-flex flex-column mx-4 my-1">
        <div class="font-weight-bold">{{ $t('agenda.leftPanel.spaces') }}</div>
        <agenda-filter-calendar-list
          ref="calendarList"
          :value="selectedOwnerIds"
          @input="selectedOwnerIds = $event" />
      </div>
      <div v-if="!$root.isMobile" class="d-flex flex-column mx-4 my-1">
        <div class="font-weight-bold">{{ $t('agenda.filter.label.advancedOptions') }}</div>
        <div class="d-flex mt-1" :title="displayWholeWeekTooltip">
          <v-checkbox
            class="my-auto ms-n1"
            ripple="false"
            :disabled="disableShowAllWeek"
            dense
            v-model="showWholeWeek"
            :label="$t('agenda.filter.label.displayWholeWeek')" />
        </div>
      </div>
    </template>
    <template slot="footer">
      <div class="d-flex">
        <v-btn
          @click="init"
          class="btn me-2">
          <v-icon
            left
            size="20"
            class="text-light-color me-3">
            fa-redo
          </v-icon>
          {{ $t('agenda.button.init') }}
        </v-btn>
        <v-spacer />
        <v-btn
          @click="cancel"
          class="btn me-2">
          {{ $t('agenda.button.cancel') }}
        </v-btn>        
        <v-btn
          @click="confirm"
          class="btn btn-primary">
          {{ $t('agenda.button.save') }}
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>
<script>
export default {
  props: {
    currentSpace: {
      type: Object,
      default: null
    },
    settings: {
      type: Object,
      default: () => null
    },
    calendarType: {
      type: String,
      default: null
    },
  },
  data: () => ({
    drawer: false,
    eventType: '',
    defaultShowWholeWeek: null,
    showWholeWeek: true,
    lastShowWholeWeek: true,
    selectedOwnerIds: [],
    lastSelectedOwnerIds: []
  }),
  created() {
    this.$root.$on('agenda-filter-drawer-open', this.open);
  },
  computed: {
    /**
     * Whether the calendar selection section is displayed in the drawer: on
     * mobile only, since desktop selects calendars through the left panel,
     * and only in a personal agenda (a space agenda displays a single space
     * calendar).
     *
     * @returns {boolean} true when the calendar selection is displayed
     */
    displayCalendarSelection() {
      return this.$root.isMobile && !eXo.env.portal.spaceId;
    },
    displayWholeWeekTooltip() {
      if (this.calendarType === 'day'|| this.calendarType === 'month') {
        return this.$t('agenda.filter.cantChangeViewAllOptionTooltip');
      }
      if (this.defaultShowWholeWeek) {
        return this.$t('agenda.filter.disabledDisplayWholeWeekTooltip');
      }
      return this.$t('agenda.filter.displayWholeWeekTooltip');
    },
    disableShowAllWeek() {
      return this.defaultShowWholeWeek || this.calendarType === 'day'|| this.calendarType === 'month';
    },
  },
  methods: {
    /**
     * Closes the drawer discarding any pending change: the whole week option
     * and the calendar selection are restored to their last applied values.
     * @returns {void}
     */
    cancel() {
      this.showWholeWeek = this.lastShowWholeWeek;
      this.selectedOwnerIds = this.lastSelectedOwnerIds;
      this.$refs.calendarFilters.close();
    },
    /**
     * Opens the drawer, initializing the filter states from the current
     * settings and, on mobile, the calendar selection list.
     * @returns {void}
     */
    open() {
      if (this.defaultShowWholeWeek === null){
        this.defaultShowWholeWeek = !this.settings.showWorkingTime;
        this.showWholeWeek = !this.settings.showWorkingTime;
      }
      if (this.calendarType === 'day') {this.showWholeWeek = false;}
      if (this.calendarType === 'month') {this.showWholeWeek = true;}
      this.lastShowWholeWeek = this.showWholeWeek;
      if (!this.eventType){
        this.eventType = this.currentSpace ? 'allEvents' : 'myEvents';
      }
      this.$refs.calendarFilters.open();
    },
    /**
     * Opens the drawer creating a personal calendar, on top of this filter
     * drawer (exo-drawer stacks the last opened drawer above the others).
     * @returns {void}
     */
    openPersonalCalendarDrawer() {
      this.$root.$emit('agenda-personal-calendar-drawer-open');
    },
    /**
     * Initializes the drawer content once the drawer is effectively opened:
     * the content is only rendered at that point, so the calendar list can
     * only be initialized from here (a ref lookup right after open() misses
     * the first opening).
     * @returns {void}
     */
    onOpened() {
      this.drawer = true;
      if (this.displayCalendarSelection) {
        // the drawer content is lazily rendered on first opening: wait for the
        // render before accessing the calendar list ref
        this.$nextTick().then(() => {
          if (this.$refs.calendarList) {
            this.$refs.calendarList.reset();
          }
        });
      }
    },
    /**
     * Applies the chosen filters: event type, whole week option and, on
     * mobile, the calendar selection, then closes the drawer.
     * @returns {void}
     */
    confirm() {
      this.$root.$emit('agenda-event-type-changed', this.eventType);
      this.$root.$emit('agenda-show-working-changed', !this.showWholeWeek);
      this.lastShowWholeWeek = this.showWholeWeek;
      if (this.displayCalendarSelection) {
        this.lastSelectedOwnerIds = this.selectedOwnerIds;
        this.$root.$emit('agenda-calendar-owners-changed', this.selectedOwnerIds);
      }
      this.$refs.calendarFilters.close();
    },
    /**
     * Resets every filter to its default value then applies the result.
     * @returns {void}
     */
    init() {
      this.eventType = this.currentSpace ? 'allEvents' : 'myEvents';
      this.showWholeWeek = this.defaultShowWholeWeek;
      this.selectedOwnerIds = [];
      this.confirm();
    },
  },
};
</script>