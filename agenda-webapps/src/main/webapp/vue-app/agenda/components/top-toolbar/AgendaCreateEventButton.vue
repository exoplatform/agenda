<template>
  <v-btn
    class="btn btn-primary"
    @click="openNewEventForm">
    <v-icon dark>
      mdi-plus
    </v-icon>
    <span class="ml-2 d-none d-lg-inline">
      {{ $t('agenda.button.addEvent') }}
    </span>
  </v-btn>
</template>
<script>
export default {
  computed: {
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs';
    },
  },
  methods:{
    openNewEventForm(){
      if (this.isMobile) {
        this.$root.$emit('agenda-event-mobile-form-open', {
          summary: '',
          startDate: new Date(),
          endDate: new Date(),
          allDay: false,
          calendar: {
            owner: {},
          },
          reminders: [],
          attachments: [],
          attendees: [],
        });
      } else {
        this.$root.$emit('agenda-event-form', {});
      }
    },
  }
};
</script>
