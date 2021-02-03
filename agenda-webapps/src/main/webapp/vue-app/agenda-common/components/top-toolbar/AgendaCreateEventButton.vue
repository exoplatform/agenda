<template>
  <div>
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
    <v-btn
      :title="$t('agenda.pendingInvitationTooltip')"
      icon
      class="primary--text"
      @click="$root.$emit('agenda-pending-invitation-drawer-open')">
      <i class="uiIcon darkGreyIcon uiIcon32x32 uiIconClock mb-1"></i>
    </v-btn>
  </div>
</template>
<script>
export default {
  methods:{
    openNewEventForm(){
      this.$root.$emit('agenda-event-form', {
        summary: '',
        allDay: false,
        calendar: {
          owner: {},
        },
        reminders: [],
        attachments: [],
        attendees: [],
      });
    },
  }
};
</script>
