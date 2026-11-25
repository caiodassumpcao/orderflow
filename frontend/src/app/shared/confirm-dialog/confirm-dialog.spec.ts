import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { fireEvent, screen } from '@testing-library/dom';
import { firstValueFrom } from 'rxjs';
import { NO_ANIMATIONS } from '../../../testing/test-support';
import { ConfirmDialog, ConfirmDialogData, ConfirmDialogResult } from './confirm-dialog';

describe('ConfirmDialog', () => {
  async function open(data: ConfirmDialogData) {
    const ref = TestBed.inject(MatDialog).open<
      ConfirmDialog,
      ConfirmDialogData,
      ConfirmDialogResult
    >(ConfirmDialog, { data });
    await firstValueFrom(ref.afterOpened());
    return firstValueFrom(ref.afterClosed());
  }

  beforeEach(() => TestBed.configureTestingModule({ providers: [NO_ANIMATIONS] }));

  afterEach(() => TestBed.inject(MatDialog).closeAll());

  it('returns the trimmed note when confirmed', async () => {
    const closed = open({
      title: 'Cancelar pedido?',
      message: 'Sem volta.',
      confirmLabel: 'Cancelar pedido',
      noteLabel: 'Motivo (opcional)',
    });
    await screen.findByRole('dialog', { name: 'Cancelar pedido?' });

    fireEvent.input(screen.getByLabelText('Motivo (opcional)'), { target: { value: '  atraso ' } });
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar pedido' }));

    await expect(closed).resolves.toBe('atraso');
  });

  it('returns undefined when the user goes back, and has no note field by default', async () => {
    const closed = open({ title: 'Enviar?', message: 'Sem volta.', confirmLabel: 'Enviar' });
    await screen.findByRole('dialog', { name: 'Enviar?' });

    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Voltar' }));

    await expect(closed).resolves.toBeUndefined();
  });
});
