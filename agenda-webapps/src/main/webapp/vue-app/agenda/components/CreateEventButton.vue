<template>
  <v-btn
    class="btn btn-primary"
    @click="openEventDialog"
  >
    <v-icon dark left>
      mdi-plus
    </v-icon>
    {{ $t('agenda.button.addEvent') }}
  </v-btn>
</template>
<script>
export default {
  methods:{
    openEventDialog(){
      this.$root.$emit('agenda-open-event-form');
    },
  }
};
</script>
