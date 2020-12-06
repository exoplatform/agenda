<template>
  <div>
    <template v-if="!showCallUrl">
      <v-btn
        class="btn primary border-radius"
        @click="generateUrl">
        {{ $t('agenda.generate.callUrl') }}
      </v-btn>
    </template>
    <template v-else>
      <span v-autolinker="callUrl"></span>
      <v-btn
        title="delete call url"
        color="grey"
        icon
        dark
        class="ml-auto"
        @click="close">
        <v-icon>
          mdi-close
        </v-icon>
      </v-btn>
    </template>
  </div>
</template>

<script>
export default {
  data: () => ({
    callUrl: 'https://community.exoplatform.com/jitsi/meet/g_agenda_ft_test',
    conferenceProviders: null,
    loading: false,
    showCallUrl: false,
  }),
  methods:{
    generateUrl(){
      if(!this.showCallUrl){
        this.showCallUrl = true;
      }
    },
    close() {
      this.showCallUrl = false;
    }
  }
};
</script>
