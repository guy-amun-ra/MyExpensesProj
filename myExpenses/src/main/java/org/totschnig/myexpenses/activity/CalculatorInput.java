package org.totschnig.myexpenses.activity;

import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.util.Utils;

import java.math.BigDecimal;
import java.math.MathContext;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Stack;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;

import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_AMOUNT;

//import android.os.Vibrator;

//TODO move to DialogFragment in order to have material styled ok and cancel buttons
/*******************************************************************************
 * Copyright (c) 2010 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * <p/>
 * Contributors:
 * Denis Solonenko - initial API and implementation
 * adapted to My Expenses by Michael Totschnig:
 * - added support for small screen height
 * - retain state on orientation change
 ******************************************************************************/
public class CalculatorInput extends ProtectedFragmentActivity implements OnClickListener {
  public static final String EXTRA_KEY_INPUT_ID = "input_id";
  public static final BigDecimal HUNDRED = new BigDecimal(100);
  public static final int[] numberButtons = { R.id.b0, R.id.b1, R.id.b2, R.id.b3, R.id.b4, R.id.b5,
      R.id.b6, R.id.b7, R.id.b8, R.id.b9 };
  public static final int[] otherButtons = { R.id.bAdd, R.id.bSubtract, R.id.bDivide, R.id.bMultiply,
      R.id.bPercent, R.id.bPlusMinus, R.id.bDot, R.id.bResult, R.id.bClear, R.id.bDelete};


  private static final BigDecimal nullValue = new BigDecimal(0);

  private TextView tvResult;
  private TextView tvOp;

  //private Vibrator vibrator;

  private Stack<String> stack = new Stack<>();
  private String result = "0";
  private boolean isRestart = true;
  private boolean isInEquals = false;
  private int lastOp = 0;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.calculator);

    //vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

    for (int i = 0; i < numberButtons.length; i++) {
      Button b = (Button) findViewById(numberButtons[i]);
      b.setOnClickListener(this);
      b.setText(Utils.toLocalizedString(i));
    }
    for (int id : otherButtons) {
      Button b = (Button) findViewById(id);
      b.setOnClickListener(this);
    }

    ((Button) findViewById(R.id.bDot)).setText(String.valueOf(Utils.getDefaultDecimalSeparator()));

    final View resultPane = findViewById(R.id.result_pane);
    resultPane.setOnLongClickListener(v -> {
      final CharSequence pasteText = ContextCompat.getSystemService(this, ClipboardManager.class).getPrimaryClip().getItemAt(0).getText();
      if (pasteText == null) return false;

      try {
        DecimalFormat df = (DecimalFormat) NumberFormat.getInstance();
        df.setParseBigDecimal(true);
        final BigDecimal parsedNumber =
            (BigDecimal) df.parseObject(pasteText.toString().replaceAll("[^\\d,.٫-]", ""));

        PopupMenu popup = new PopupMenu(CalculatorInput.this, resultPane);
        popup.setOnMenuItemClickListener(item -> {
          setDisplay(parsedNumber.toPlainString());
          return true;
        });
        popup.inflate(R.menu.paste);
        popup.show();
        return true;
      } catch (ParseException e) {
        return false;
      }
    });

    tvResult = (TextView) findViewById(R.id.result);
    setDisplay("0");
    tvOp = (TextView) findViewById(R.id.op);

    Button b = (Button) findViewById(R.id.bOK);

    b.setOnClickListener(arg0 -> {
      if (!isInEquals) {
        doEqualsChar();
      }
      close();
    });
    b = (Button) findViewById(R.id.bCancel);
    b.setOnClickListener(arg0 -> {
      setResult(RESULT_CANCELED);
      finish();
    });

    Intent intent = getIntent();
    if (intent != null) {
      BigDecimal amount = (BigDecimal) intent.getSerializableExtra(KEY_AMOUNT);
      if (amount != null) {
        setDisplay(amount.toPlainString());
      }
    }
  }

  @Override
  public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
    menu.add(0, v.getId(), 0, "Paste");
  }

  @Override
  public void onClick(View v) {
    onButtonClick(v.getId());
  }

  private void setDisplay(String s) {
    if (isNotEmpty(s)) {
      s = s.replaceAll(",", ".");
      result = s;
      tvResult.setText(localize(result));
    }
  }

  private String localize(String in) {
    StringBuilder out = new StringBuilder();
    for (char c : in.toCharArray()) {
      if (Character.isDigit(c)) {
        out.append(Utils.toLocalizedString(Character.getNumericValue(c)));
      } else if (c == '.') {
        out.append(Utils.getDefaultDecimalSeparator());
      } else {
        out.append(c);
      }
    }
    return out.toString();
  }

  public static boolean isNotEmpty(String s) {
    return s != null && s.length() > 0;
  }

  private void onButtonClick(int id) {
/*        if (vibrator != null) {
            vibrator.vibrate(20);
        }*/
    if (id == R.id.bClear) {
      resetAll();
    } else if (id == R.id.bDelete) {
      doBackspace();
    } else {
      doButton(id);
    }
  }

  private void resetAll() {
    setDisplay("0");
    tvOp.setText("");
    lastOp = 0;
    isRestart = true;
    stack.clear();
  }

  private void doBackspace() {
    String s = result;
    if ("0".equals(s) || isRestart) {
      return;
    }
    String newDisplay = s.length() > 1 ? s.substring(0, s.length() - 1) : "0";
    if ("-".equals(newDisplay)) {
      newDisplay = "0";
    }
    setDisplay(newDisplay);
  }

  private void doButton(int id) {
    if (id == R.id.b0) {
      addChar('0');
    } else if (id == R.id.b1) {
      addChar('1');
    } else if (id == R.id.b2) {
      addChar('2');
    } else if (id == R.id.b3) {
      addChar('3');
    } else if (id == R.id.b4) {
      addChar('4');
    } else if (id == R.id.b5) {
      addChar('5');
    } else if (id == R.id.b6) {
      addChar('6');
    } else if (id == R.id.b7) {
      addChar('7');
    } else if (id == R.id.b8) {
      addChar('8');
    } else if (id == R.id.b9) {
      addChar('9');
    } else if (id == R.id.bDot) {
      addChar('.');
    } else if (id == R.id.bAdd || id == R.id.bSubtract || id == R.id.bMultiply || id == R.id.bDivide) {
      doOpChar(id);
    } else if (id == R.id.bPercent) {
      doPercentChar();
    } else if (id == R.id.bResult) {
      doEqualsChar();
    } else if (id == R.id.bPlusMinus) {
      setDisplay(new BigDecimal(result).negate().toPlainString());
    }
  }

  private void addChar(char c) {
    String s = result;
    if (c == '.' && s.indexOf('.') != -1 && !isRestart) {
      return;
    }
    if (isRestart) {
      setDisplay(c == '.' ? "0." : String.valueOf(c));
      isRestart = false;
    } else {
      if ("0".equals(s) && c != '.') {
        s = String.valueOf(c);
      } else {
        s += c;
      }
      setDisplay(s);
    }
  }

  private void doOpChar(int op) {
    if (isInEquals) {
      stack.clear();
      isInEquals = false;
    }
    stack.push(result);
    doLastOp();
    lastOp = op;
    tvOp.setText(getLastOpLabel());
  }

  @NonNull
  private String getLastOpLabel() {
    if (lastOp == R.id.bAdd) {
      return getString(R.string.calculator_operator_plus);
    } else if (lastOp == R.id.bSubtract) {
      return getString(R.string.calculator_operator_minus);
    } else if (lastOp == R.id.bMultiply) {
      return getString(R.string.calculator_operator_multiply);
    } else if (lastOp == R.id.bDivide) {
      return getString(R.string.calculator_operator_divide);
    }
    return "";
  }

  private void doLastOp() {
    isRestart = true;
    if (lastOp == 0 || stack.size() == 1) {
      return;
    }

    String valTwo = stack.pop();
    String valOne = stack.pop();
    if (lastOp == R.id.bAdd) {
      stack.push(new BigDecimal(valOne).add(new BigDecimal(valTwo)).toPlainString());
    } else if (lastOp == R.id.bSubtract) {
      stack.push(new BigDecimal(valOne).subtract(new BigDecimal(valTwo)).toPlainString());
    } else if (lastOp == R.id.bMultiply) {
      stack.push(new BigDecimal(valOne).multiply(new BigDecimal(valTwo)).toPlainString());
    } else if (lastOp == R.id.bDivide) {
      BigDecimal d2 = new BigDecimal(valTwo);
      if (d2.compareTo(nullValue) == 0) {
        stack.push("0.0");
      } else {
        stack.push(new BigDecimal(valOne).divide(d2, MathContext.DECIMAL64).toPlainString());
      }
    }
    setDisplay(stack.peek());
    if (isInEquals) {
      stack.push(valTwo);
    }
  }

  private void doPercentChar() {
    if (stack.isEmpty()) return;
    //noinspection BigDecimalMethodWithoutRoundingCalled
    setDisplay(new BigDecimal(result).divide(HUNDRED).multiply(new BigDecimal(stack.peek()))
        .toPlainString());
    tvOp.setText("");
  }

  private void doEqualsChar() {
    if (lastOp == 0) {
      return;
    }
    if (!isInEquals) {
      isInEquals = true;
      stack.push(result);
    }
    doLastOp();
    tvOp.setText("");
  }

  private void close() {
    Intent data = new Intent();
    data.putExtra(KEY_AMOUNT, result);
    data.putExtra(EXTRA_KEY_INPUT_ID, getIntent().getIntExtra(EXTRA_KEY_INPUT_ID, 0));
    setResult(RESULT_OK, data);
    finish();
  }

  @Override
  protected void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putString("result", result);
    outState.putInt("lastOp", lastOp);
    outState.putBoolean("isInEquals", isInEquals);
    outState.putSerializable("stack", stack.toArray(new String[0]));
  }

  @Override
  protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);
    result = savedInstanceState.getString("result");
    lastOp = savedInstanceState.getInt("lastOp");
    isInEquals = savedInstanceState.getBoolean("isInEquals");
    stack = new Stack<>();
    stack.addAll(Arrays.asList((String[]) savedInstanceState.getSerializable("stack")));
    if (lastOp != 0 && !isInEquals) tvOp.setText(getLastOpLabel());
    setDisplay(result);
  }

  @Override
  protected int getSnackbarContainerId() {
    return R.id.Calculator;
  }
}
