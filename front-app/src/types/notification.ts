export interface Notificacao {
  idNotificacao: string;
  idProprietario: string;
  idPedido?: string;
  titulo: string;
  mensagem: string;
  lida: boolean;
  dataGeracao: Date;
  dataLeitura?: Date;
}
