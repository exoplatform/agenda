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
      <div class="radio-group-container ps-4">
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
          @click="close"
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
  }),
  created() {
    this.$root.$on('agenda-filter-drawer-open', this.open);
  },
  methods: {
    close() {
      this.$refs.calendarFilters.close();
    },
    open(currentSpace) {
      this.currentSpace = currentSpace;
      if (!this.eventType){
        this.eventType = currentSpace ? 'allEvent' : 'myEvent';
      }
      this.$refs.calendarFilters.open();
    },
    confirm() {
      this.$root.$emit('agenda-event-type-changed', this.eventType);
      this.close();
    },
    init() {
      this.eventType = this.currentSpace ? 'allEvent' : 'myEvent';
      this.confirm();
    },
  },
};
</script>