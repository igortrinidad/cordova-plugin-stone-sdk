package br.com.stone.cordova.sdk;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.widget.Toast;
import android.util.Log;

import org.apache.cordova.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import stone.application.StoneStart;
import stone.application.enums.ErrorsEnum;
import stone.application.interfaces.StoneCallbackInterface;
import stone.database.transaction.TransactionDAO;
import stone.providers.ActiveApplicationProvider;
import stone.providers.BluetoothConnectionProvider;
import stone.providers.CancellationProvider;
import stone.providers.DisplayMessageProvider;
import stone.user.UserModel;
import stone.utils.GlobalInformations;
import stone.utils.PinpadObject;
import stone.cache.ApplicationCache;
import stone.environment.Environment;

import stone.application.enums.Action;
import stone.application.enums.InstalmentTransactionEnum;
import stone.application.enums.TransactionStatusEnum;
import stone.application.enums.TypeOfTransactionEnum;
import stone.application.interfaces.StoneActionCallback;
import stone.database.transaction.TransactionObject;
import stone.providers.TransactionProvider;
import stone.utils.Stone;

import static stone.environment.Environment.PRODUCTION;
import static stone.environment.Environment.SANDBOX;

public class StoneSDK extends CordovaPlugin {

    static BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    private static final String DEVICE = "device";
    private static final String DEVICE_SELECTED = "deviceSelected";
    private static final String SET_ENVIRONMENT = "setEnvironment";
    private static final String TRANSACTION = "transaction";
    private static final String TRANSACTION_CANCEL = "transactionCancel";
    private static final String TRANSACTION_LIST = "transactionList";
    private static final String VALIDATION = "validation";
    private static final String DISPLAY_MESSAGE = "displayMessage";

    public boolean execute(String action, JSONArray data, CallbackContext callbackContext) throws JSONException {

        if (action.equals(DEVICE)) {
            turnBluetoothOn();
            bluetoothList(callbackContext);
            return true;
        } else if (action.equals(DEVICE_SELECTED)) {
            bluetoothSelected(data, callbackContext);
            return true;
        } else if (action.equals(TRANSACTION)) {
            transaction(data, callbackContext);
            return true;
        } else if (action.equals(TRANSACTION_CANCEL)) {
            transactionCancel(data, callbackContext);
            return true;
        } else if (action.equals(TRANSACTION_LIST)) {
            transactionList(callbackContext);
            return true;
        } else if (action.equals(VALIDATION)) {
            List<UserModel> user = StoneStart.init(this.cordova.getActivity());
            Stone.setAppName("RootBurgerPos");
            Stone.setEnvironment(PRODUCTION);
            if (user == null) {
                stoneCodeValidation(data, callbackContext);
                return true;
            } else {
                Toast.makeText(StoneSDK.this.cordova.getActivity(), "Stone Code já validado", Toast.LENGTH_SHORT).show();
                return true;
            }
        } else if (action.equals(SET_ENVIRONMENT)) {
            setEnvironment(data);
            return true;
        } else if (action.equals(DISPLAY_MESSAGE)) {
            displayMessage(data, callbackContext);
            return true;
        } else {
            return false;
        }
    }

    

    public void turnBluetoothOn() {
        try {
            mBluetoothAdapter.enable();
            do {
            } while (!mBluetoothAdapter.isEnabled());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void bluetoothList(CallbackContext callbackContext) throws JSONException {
        // Lista de Pinpads para passar para o BluetoothConnectionProvider.
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        JSONArray arrayList = new JSONArray();

        // Lista todos os dispositivos pareados.
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                String name = device.getName();
                String address = device.getAddress();
                arrayList.put(name + "_" + address);
            }
            callbackContext.success(arrayList);
        }
    }

    private void bluetoothSelected(JSONArray data, final CallbackContext callbackContext) throws JSONException {
        // Pega o pinpad selecionado.
        String arrayList = data.getString(0);

        String[] parts = arrayList.split("_");

        String name = parts[0];
        String macAddress = parts[1];

        PinpadObject pinpad = new PinpadObject(name, macAddress, false);

        // Passa o pinpad selecionado para o provider de conexao bluetooth.
        BluetoothConnectionProvider bluetoothConnectionProvider = new BluetoothConnectionProvider(StoneSDK.this.cordova.getActivity(), pinpad);
        bluetoothConnectionProvider.setDialogMessage("Criando conexao com o pinpad selecionado"); // Mensagem exibida do dialog.
        bluetoothConnectionProvider.setWorkInBackground(false); // Informa que havera um feedback para o usuario.
        bluetoothConnectionProvider.setConnectionCallback(new StoneCallbackInterface() {

            public void onSuccess() {
                Toast.makeText(StoneSDK.this.cordova.getActivity(), "Pinpad conectado", Toast.LENGTH_SHORT).show();
                callbackContext.success();
            }

            public void onError() {
                Toast.makeText(StoneSDK.this.cordova.getActivity(), "Erro durante a conexao. Verifique a lista de erros do provider para mais informacoes", Toast.LENGTH_SHORT).show();
                callbackContext.error("Erro durante a conexao. Verifique a lista de erros do provider para mais informacoes");
            }

        });
        bluetoothConnectionProvider.execute(); // Executa o provider de conexao bluetooth.
    }

    private void stoneCodeValidation(JSONArray data, final CallbackContext callbackContext) throws JSONException {
        List<String> stoneCodeList = new ArrayList<String>();

        // Adicione seu Stonecode abaixo, como string.
        stoneCodeList.add(data.getString(0)); // coloque seu Stone Code aqui

        final ActiveApplicationProvider provider = new ActiveApplicationProvider(StoneSDK.this.cordova.getActivity());
        provider.setDialogMessage("Ativando o aplicativo...");
        provider.setDialogTitle("Aguarde");
        provider.useDefaultUI(false);
        provider.setConnectionCallback(new StoneCallbackInterface() {
            /* Metodo chamado se for executado sem erros */
            public void onSuccess() {
                Toast.makeText(StoneSDK.this.cordova.getActivity(), "Ativado com sucesso, iniciando o aplicativo", Toast.LENGTH_SHORT).show();
                callbackContext.success("Ativado com sucesso");
            }

            /* metodo chamado caso ocorra alguma excecao */
            public void onError() {
                Toast.makeText(StoneSDK.this.cordova.getActivity(), "Erro na ativacao do aplicativo, verifique a lista de erros do provider", Toast.LENGTH_SHORT).show();

                /* Chame o metodo abaixo para verificar a lista de erros. Para mais detalhes, leia a documentacao: */
                callbackContext.error(provider.getListOfErrors().toString());

            }
        });

        provider.activate(stoneCodeList);
        
    }

    private void setEnvironment(JSONArray data) throws JSONException {
        String env = data.getString(0);
        Stone.setEnvironment(Environment.valueOf(env));
    }

    private void transactionList(CallbackContext callbackContext) {
        // acessa todas as transacoes do banco de dados
        TransactionDAO transactionDAO = new TransactionDAO(StoneSDK.this.cordova.getActivity());

        // cria uma lista com todas as transacoes
        List<TransactionObject> transactionObjects = transactionDAO.getAllTransactionsOrderByIdDesc();

        // exibe todas as transacoes (neste caso valor e status) para o usuario
        JSONArray arrayList = new JSONArray();

        for (TransactionObject list : transactionObjects) {
            JSONObject obj = new JSONObject();

            try{
                obj.put("idFromBase", String.valueOf(list.getIdFromBase()));
                obj.put("amount",  list.getAmount());
                obj.put("requestId",   String.valueOf(list.getRequestId()));
                obj.put("emailSent",   String.valueOf(list.getEmailSent()));
                obj.put("timeToPassTransaction",   String.valueOf(list.getTimeToPassTransaction()));
                obj.put("initiatorTransactionKey",   String.valueOf(list.getInitiatorTransactionKey()));
                obj.put("recipientTransactionIdentification",   String.valueOf(list.getRecipientTransactionIdentification()));
                obj.put("cardHolderNumber",   String.valueOf(list.getCardHolderNumber()));
                obj.put("cardHolderName",   String.valueOf(list.getCardHolderName()).trim());
                obj.put("date",   String.valueOf(list.getDate()));
                obj.put("time",   String.valueOf(list.getTime()));
                obj.put("aid",   String.valueOf(list.getAid()));
                obj.put("arcq",   String.valueOf(list.getArcq()));
                obj.put("authorizationCode",   String.valueOf(list.getAuthorizationCode()));
                obj.put("iccRelatedData",   String.valueOf(list.getIccRelatedData()));
                obj.put("transactionReference",   String.valueOf(list.getTransactionReference()));
                obj.put("actionCode",   String.valueOf(list.getActionCode()));
                obj.put("commandActionCode",   String.valueOf(list.getCommandActionCode()));
                obj.put("pinpadUsed",   String.valueOf(list.getPinpadUsed()));
                obj.put("cne",   String.valueOf(list.getCne()));
                obj.put("cvm",   String.valueOf(list.getCvm()));
                obj.put("serviceCode",   String.valueOf(list.getServiceCode()));
                obj.put("entryMode",   String.valueOf(list.getEntryMode()));
                obj.put("cardBrand",   String.valueOf(list.getCardBrand()));
                obj.put("instalmentTransaction",   String.valueOf(list.getInstalmentTransaction()));
                obj.put("transactionStatus",   String.valueOf(list.getTransactionStatus()));
                obj.put("instalmentType",   String.valueOf(list.getInstalmentType()));
                obj.put("typeOfTransactionEnum",   String.valueOf(list.getTypeOfTransactionEnum()));
                obj.put("cancellationDate",   String.valueOf(list.getCancellationDate()));

                arrayList.put(obj);

            }catch (JSONException e){
                e.printStackTrace();
            }
        }
        callbackContext.success(arrayList);
    }

    private void transactionCancel(JSONArray data, final CallbackContext callbackContext) throws JSONException {
        System.out.println("Opcao Selecionada Cancel");

        String transactionCode = data.getString(0);
        System.out.println("optSelected: " + transactionCode);

        // Pega o id da transacao selecionada.
        String[] parts = transactionCode.split("_");

        String idOptSelected = parts[0];
        System.out.println("idOptSelected: " + idOptSelected);

        //l�gica do cancelamento
        final int transacionId = Integer.parseInt(idOptSelected);

        final CancellationProvider cancellationProvider = new CancellationProvider(StoneSDK.this.cordova.getActivity(), transacionId, Stone.getUserModel(0));
        cancellationProvider.setWorkInBackground(false); // para dar feedback ao usuario ou nao.
        //cancellationProvider.setDialogMessage("Cancelando...");
        cancellationProvider.setConnectionCallback(new StoneCallbackInterface() { // chamada de retorno.
            public void onSuccess() {
                Toast.makeText(StoneSDK.this.cordova.getActivity(), cancellationProvider.getMessageFromAuthorize(), Toast.LENGTH_SHORT).show();
                callbackContext.success("Cancelado com sucesso");
            }

            public void onError() {
                Toast.makeText(StoneSDK.this.cordova.getActivity(), "Um erro ocorreu durante o cancelamento com a transacao de id: " + transacionId, Toast.LENGTH_SHORT).show();
                callbackContext.error(cancellationProvider.getListOfErrors().toString() + " Erro ocorreu durante o cancelamento da transacao de id: " + transacionId);
            }
        });
        cancellationProvider.execute();
    }

    /**
    * Transactions
    */
    private void transaction(JSONArray data, final CallbackContext callbackContext) throws JSONException {

        String amount = data.getString(0);
        String method = data.getString(1);
        String instalments = data.getString(2);
        String success_message = data.getString(3);
        String your_unique_id = data.getString(4);

        Toast.makeText(StoneSDK.this.cordova.getActivity().getApplicationContext(), "Method: " + method, Toast.LENGTH_SHORT).show();
        Toast.makeText(StoneSDK.this.cordova.getActivity().getApplicationContext(), "Valor: " + amount, Toast.LENGTH_SHORT).show();

        System.out.println("getAmount: " + amount);

        // Cria o objeto de transacao.
        TransactionObject transactionObject = new TransactionObject();

        // A seguir deve-se popular o objeto.
        transactionObject.setAmount(amount);
        transactionObject.setInstalmentTransaction(InstalmentTransactionEnum.getAt(0));

        // Verifica a forma de pagamento selecionada.
        if (method == "DEBIT") {
            transactionObject.setTypeOfTransaction(TypeOfTransactionEnum.DEBIT);
        } else {
            transactionObject.setTypeOfTransaction(TypeOfTransactionEnum.CREDIT);
        }

        transactionObject.setUserModel(Stone.getUserModel(0));
        //transactionObject.setSignature(BitmapFactory.decodeResource(getResources(), R.drawable.signature));
        transactionObject.setCapture(true);

        transactionObject.setSubMerchantCity("BH"); //Cidade do sub-merchant
        transactionObject.setSubMerchantPostalAddress("31160370"); //CEP do sub-merchant (Apenas números)
        transactionObject.setSubMerchantRegisteredIdentifier("00000000"); // Identificador do sub-merchant
        transactionObject.setSubMerchantTaxIdentificationNumber("11834266000318"); // CNPJ do sub-merchant (apenas números)

        // Seleciona o mcc do lojista.
        transactionObject.setSubMerchantCategoryCode("123");

        // Seleciona o endereço do lojista.
        transactionObject.setSubMerchantAddress("R. Alberto Cintra, 135");

        // AVISO IMPORTANTE: Nao e recomendado alterar o campo abaixo do
        // ITK, pois ele gera um valor unico. Contudo, caso seja
        // necessario, faca conforme a linha abaixo.
        transactionObject.setInitiatorTransactionKey(your_unique_id);

        // Processo para envio da transacao.
        final TransactionProvider provider = new TransactionProvider(
                StoneSDK.this.cordova.getActivity(),
                transactionObject,
                Stone.getUserModel(0),
                Stone.getPinpadFromListAt(0)
        );

        provider.useDefaultUI(false);
        provider.setDialogMessage("Enviando..");
        provider.setDialogTitle("Aguarde");

        provider.setConnectionCallback(new StoneActionCallback() {
            @Override
            public void onStatusChanged(Action action) {
                Log.d("TRANSACTION_STATUS", action.name());
            }

            public void onSuccess() {
                if (provider.getTransactionStatus() == TransactionStatusEnum.APPROVED) {
                    Toast.makeText(StoneSDK.this.cordova.getActivity().getApplicationContext(), "Transação enviada com sucesso e salva no banco. Para acessar, use o TransactionDAO.", Toast.LENGTH_SHORT).show();
                    callbackContext.success("Transação enviada com sucesso e salva no banco. Para acessar, use o TransactionDAO.");
                } else {
                    Toast.makeText(StoneSDK.this.cordova.getActivity().getApplicationContext(), "Erro na transação: \"" + provider.getMessageFromAuthorize() + "\"", Toast.LENGTH_LONG).show();
                }
            }

            public void onError() {
                Toast.makeText(StoneSDK.this.cordova.getActivity().getApplicationContext(), "Erro na transação", Toast.LENGTH_SHORT).show();
                callbackContext.error(provider.getListOfErrors().toString());
            }
        });
        provider.execute();
    }


    /*
    * Display a message on connected PinPad
    */
    public void displayMessage(JSONArray data, final CallbackContext callbackContext) throws JSONException {

        String deviceName = data.getString(0);
        String deviceMacAddress = data.getString(1);
        String messageToDisplay = data.getString(2);

        PinpadObject pinpad = new PinpadObject(deviceName, deviceMacAddress, false);

        DisplayMessageProvider displayMessageProvider = new DisplayMessageProvider(StoneSDK.this.cordova.getActivity(), messageToDisplay, pinpad);

        displayMessageProvider.setDialogMessage("Ativando o aplicativo...");
        displayMessageProvider.setDialogTitle("Aguarde");

        displayMessageProvider.setWorkInBackground(false); // informa se este provider ira rodar em background ou nao
        displayMessageProvider.setConnectionCallback(new StoneCallbackInterface() {

            public void onSuccess() {
                Toast.makeText(StoneSDK.this.cordova.getActivity(), "Msg enviada com sucesso", Toast.LENGTH_SHORT).show();
                callbackContext.success("Ativado com sucesso");
            }

            public void onError() {
                Toast.makeText(StoneSDK.this.cordova.getActivity(), "Erro no envio da mensagem", Toast.LENGTH_SHORT).show();
                callbackContext.error(displayMessageProvider.getListOfErrors().toString());
            }

        });

        displayMessageProvider.execute();

    }

}
