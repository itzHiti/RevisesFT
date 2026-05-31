package kz.itzhiti.revisesft.revise;

public enum ReviseState {
    PENDING, // Ожидание движения игрока
    STARTED, // Таймер идёт
    REMOTE // Режим AnyDesk (таймер остановлен)
}