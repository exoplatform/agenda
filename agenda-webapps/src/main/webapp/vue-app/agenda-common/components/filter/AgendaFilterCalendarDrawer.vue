<template>
  <exo-drawer
    ref="calendarFilters"
    right
    @opened="drawer = true"
    @closed="drawer = false">
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
              value="myEvent" />
            <v-radio
              :label="$t('agenda.declinedEvent')"
              value="declinedEvent" />
            <v-radio
              :label="$t('agenda.allEvent')"
              value="allEvent" />
          </v-radio-group>
        </div>
      </div>
      <div class="d-flex flex-column mx-4 my-1">
        <div class="font-weight-bold">{{ $t('agenda.filter.label.advancedOptions') }}</div>
        <div class="d-flex mt-1">
          <v-checkbox
            class="my-auto ms-n1"
            ripple="false"
            dense
            v-model="showWholeWeek" />
          <label class="switch-label-text text-subtitle-1 my-auto">{{ $t('agenda.filter.label.displayWholeWeek') }}</label>
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
  data: () => ({
    drawer: false,
    eventType: '',
    currentSpace: '',
    defaultShowWholeWeek: null,
    showWholeWeek: true,
    lastShowWholeWeek: true
  }),
  created() {
    this.$root.$on('agenda-filter-drawer-open', this.open);
  },
  methods: {
    cancel() {
      this.showWholeWeek = this.lastShowWholeWeek;
      this.$refs.calendarFilters.close();
    },
    open(currentSpace,settings) {
      this.currentSpace = currentSpace;
      if (this.defaultShowWholeWeek === null){
        this.defaultShowWholeWeek = !settings.showWorkingTime;
        this.showWholeWeek = !settings.showWorkingTime;
      }
      this.lastShowWholeWeek = this.showWholeWeek;
      if (!this.eventType){
        this.eventType = currentSpace ? 'allEvent' : 'myEvent';
      }
      this.$refs.calendarFilters.open();
    },
    confirm() {
      this.$root.$emit('agenda-event-type-changed', this.eventType);
      this.$root.$emit('agenda-show-working-changed', !this.showWholeWeek);
      this.$refs.calendarFilters.close();
    },
    init() {
      this.eventType = this.currentSpace ? 'allEvent' : 'myEvent';
      this.showWholeWeek = this.defaultShowWholeWeek;
      this.confirm();
    },
  },
};
</script>